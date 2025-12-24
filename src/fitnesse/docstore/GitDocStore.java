package fitnesse.docstore;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class GitDocStore implements DocStore {
  private static final String CONTENT_FILE = "content.txt";
  private static final String PROPERTIES_FILE = "properties.xml";
  private static final String ATTACHMENTS_DIR = "files";

  private final Vertx vertx;
  private final FileSystem fs;
  private final Path repoRoot;
  private final GitRepository git;
  private final String commitMessageTemplate;
  private final MergeStrategy mergeStrategy;
  private final GitCommitConfig commitConfig;

  public GitDocStore(Path repoRoot) {
    this(Vertx.vertx(), repoRoot, "wiki: update %s", MergeStrategy.fromConfig());
  }

  public GitDocStore(Path repoRoot, String commitMessageTemplate) {
    this(Vertx.vertx(), repoRoot, commitMessageTemplate, MergeStrategy.fromConfig());
  }

  public GitDocStore(Path repoRoot, String commitMessageTemplate, MergeStrategy mergeStrategy) {
    this(Vertx.vertx(), repoRoot, commitMessageTemplate, mergeStrategy);
  }

  public GitDocStore(Vertx vertx, Path repoRoot, String commitMessageTemplate, MergeStrategy mergeStrategy) {
    this.vertx = vertx;
    this.fs = vertx.fileSystem();
    this.repoRoot = repoRoot;
    this.commitMessageTemplate = commitMessageTemplate;
    this.git = new GitRepository(repoRoot);
    this.mergeStrategy = mergeStrategy == null ? MergeStrategy.FAST_FORWARD : mergeStrategy;
    this.commitConfig = GitCommitConfig.fromEnv();
    initRepoIfNeeded();
  }

  @Override
  public PageRef resolve(String wikiPath) {
    String normalized = (wikiPath == null || wikiPath.isEmpty()) ? "FrontPage" : wikiPath;
    return new PageRef(normalized);
  }

  @Override
  public Page readPage(PageRef ref) {
    Path pageDir = pageDir(ref);
    String content = readIfExists(pageDir.resolve(CONTENT_FILE));
    String properties = readIfExists(pageDir.resolve(PROPERTIES_FILE));
    return new Page(ref, content, properties);
  }

  @Override
  public void writePage(PageRef ref, PageWriteRequest req) {
    Path pageDir = pageDir(ref);
    String expected = req == null ? null : req.expectedVersion();
    if (expected == null || expected.isEmpty()) {
      writeAndCommit(ref, req, pageDir);
      return;
    }
    String current = git.currentCommit();
    if (current == null || current.isEmpty() || current.equals(expected)) {
      writeAndCommit(ref, req, pageDir);
      return;
    }
    applyWriteWithMerge(ref, req, pageDir, expected);
  }

  @Override
  public List<PageRef> listChildren(PageRef ref) {
    Path pageDir = pageDir(ref);
    List<PageRef> children = new ArrayList<>();
    if (!isDirectory(pageDir)) {
      return children;
    }
    try {
      for (String child : fs.readDirBlocking(pageDir.toString())) {
        if (isDirectory(Path.of(child))) {
          Path path = Path.of(child);
          children.add(new PageRef(ref.wikiPath() + "/" + path.getFileName().toString()));
        }
      }
      return children;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to list children for " + ref.wikiPath(), e);
    }
  }

  @Override
  public PageProperties readProperties(PageRef ref) {
    Path propsPath = pageDir(ref).resolve(PROPERTIES_FILE);
    if (!fs.existsBlocking(propsPath.toString())) {
      return new PageProperties("");
    }
    return new PageProperties(readIfExists(propsPath));
  }

  @Override
  public void writeProperties(PageRef ref, PageProperties props) {
    Path propsPath = pageDir(ref).resolve(PROPERTIES_FILE);
    try {
      fs.mkdirsBlocking(propsPath.getParent().toString());
      fs.writeFileBlocking(propsPath.toString(), Buffer.buffer(safe(props.xml()), StandardCharsets.UTF_8.name()));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write properties for " + ref.wikiPath(), e);
    }
    commitPaths("properties", ref, GitIdentityHolder.current(), propsPath);
  }

  @Override
  public List<AttachmentRef> listAttachments(PageRef ref) {
    Path attachmentsDir = pageDir(ref).resolve(ATTACHMENTS_DIR);
    List<AttachmentRef> attachments = new ArrayList<>();
    if (!isDirectory(attachmentsDir)) {
      return attachments;
    }
    try {
      for (String child : fs.readDirBlocking(attachmentsDir.toString())) {
        if (isRegularFile(Path.of(child))) {
          Path path = Path.of(child);
          attachments.add(new AttachmentRef(ref, path.getFileName().toString()));
        }
      }
      return attachments;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to list attachments for " + ref.wikiPath(), e);
    }
  }

  @Override
  public InputStream readAttachment(AttachmentRef ref) {
    Path attachmentPath = pageDir(ref.pageRef()).resolve(ATTACHMENTS_DIR).resolve(ref.name());
    try {
      Buffer data = fs.readFileBlocking(attachmentPath.toString());
      return new ByteArrayInputStream(data.getBytes());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read attachment " + ref.name(), e);
    }
  }

  @Override
  public void writeAttachment(AttachmentRef ref, InputStream data, Metadata meta) {
    Path attachmentPath = pageDir(ref.pageRef()).resolve(ATTACHMENTS_DIR).resolve(ref.name());
    try {
      fs.mkdirsBlocking(attachmentPath.getParent().toString());
      fs.writeFileBlocking(attachmentPath.toString(), Buffer.buffer(readAllBytes(data)));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write attachment " + ref.name(), e);
    }
    commitPaths("attachment", ref.pageRef(), GitIdentityHolder.current(), attachmentPath);
  }

  @Override
  public PageHistory history(PageRef ref, HistoryQuery q) {
    int limit = q == null ? HistoryQuery.defaultQuery().limit() : q.limit();
    List<String> lines = git.log(relativePath(ref), limit);
    List<PageHistoryEntry> entries = new ArrayList<>();
    for (String line : lines) {
      PageHistoryEntry entry = parseLogLine(line);
      if (entry != null) {
        entries.add(entry);
      }
    }
    return new PageHistory(entries);
  }

  private void initRepoIfNeeded() {
    if (!isDirectory(repoRoot.resolve(".git"))) {
      try {
        fs.mkdirsBlocking(repoRoot.toString());
      } catch (Exception e) {
        throw new IllegalStateException("Failed to create repo directory " + repoRoot, e);
      }
      git.init();
    }
  }

  private void commitPaths(String reason, PageRef ref, GitIdentity author, Path... paths) {
    List<Path> toAdd = new ArrayList<>();
    for (Path path : paths) {
      if (path != null && fs.existsBlocking(path.toString())) {
        toAdd.add(path);
      }
    }
    if (toAdd.isEmpty()) {
      return;
    }
    String message = String.format(commitMessageTemplate, ref.wikiPath());
    git.add(toAdd);
    git.commit(message + " (" + reason + ")", author, commitConfig);
  }

  private void writeAndCommit(PageRef ref, PageWriteRequest req, Path pageDir) {
    try {
      fs.mkdirsBlocking(pageDir.toString());
      fs.writeFileBlocking(pageDir.resolve(CONTENT_FILE).toString(),
        Buffer.buffer(safe(req.content()), StandardCharsets.UTF_8.name()));
      if (req.propertiesXml() != null) {
        fs.writeFileBlocking(pageDir.resolve(PROPERTIES_FILE).toString(),
          Buffer.buffer(req.propertiesXml(), StandardCharsets.UTF_8.name()));
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write page " + ref.wikiPath(), e);
    }
    commitPaths("page", ref, resolveAuthor(req), pageDir.resolve(CONTENT_FILE), pageDir.resolve(PROPERTIES_FILE));
  }

  private void applyWriteWithMerge(PageRef ref, PageWriteRequest req, Path pageDir, String expected) {
    String currentCommit = git.currentCommit();
    String currentBranch = git.currentBranch();
    String restoreRef = "HEAD".equals(currentBranch) ? currentCommit : currentBranch;
    String tempBranch = "fitnesse-tmp-" + System.currentTimeMillis();
    try {
      git.createBranch(tempBranch, expected);
      git.checkout(tempBranch);
      writeAndCommit(ref, req, pageDir);
      git.checkout(restoreRef);
      switch (mergeStrategy) {
        case OURS:
          git.mergeOurs(tempBranch);
          break;
        case THEIRS:
          git.mergeTheirs(tempBranch);
          break;
        case MERGE_COMMIT:
          git.mergeNoFastForward(tempBranch);
          break;
        case REBASE:
          git.checkout(tempBranch);
          git.rebase(restoreRef);
          git.checkout(restoreRef);
          git.mergeFastForward(tempBranch);
          break;
        case SQUASH:
          git.mergeSquash(tempBranch);
          git.commit(String.format(commitMessageTemplate, ref.wikiPath()) + " (squash)", resolveAuthor(req), commitConfig);
          break;
        case FAST_FORWARD:
        default:
          git.mergeFastForward(tempBranch);
          break;
      }
    } catch (IllegalStateException e) {
      abortMergeOps();
      throw conflictOrRethrow(ref, expected, e);
    } finally {
      try {
        git.checkout(restoreRef);
      } catch (Exception ignored) {
        // Ignore checkout failures when cleaning up.
      }
      try {
        git.deleteBranch(tempBranch);
      } catch (Exception ignored) {
        // Ignore branch cleanup failures.
      }
    }
  }

  private RuntimeException conflictOrRethrow(PageRef ref, String expected, IllegalStateException error) {
    String message = error.getMessage() == null ? "" : error.getMessage();
    if (message.contains("CONFLICT") || message.contains("Merge conflict")) {
      return new DocStoreConflictException("Conflict writing " + ref.wikiPath()
        + ": expected " + expected + " (" + mergeStrategy + ")");
    }
    return error;
  }

  private void abortMergeOps() {
    try {
      git.abortMerge();
    } catch (Exception ignored) {
      // Ignore merge abort failures.
    }
    try {
      git.abortRebase();
    } catch (Exception ignored) {
      // Ignore rebase abort failures.
    }
  }

  private Path pageDir(PageRef ref) {
    String[] parts = ref.wikiPath().split("/");
    Path current = repoRoot;
    for (String part : parts) {
      if (!part.isEmpty()) {
        current = current.resolve(part);
      }
    }
    return current;
  }

  private String relativePath(PageRef ref) {
    return ref.wikiPath().replace("\\", "/");
  }

  private String readIfExists(Path path) {
    if (!fs.existsBlocking(path.toString())) {
      return "";
    }
    try {
      Buffer data = fs.readFileBlocking(path.toString());
      return data.toString(StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read " + path, e);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private boolean isDirectory(Path path) {
    if (!fs.existsBlocking(path.toString())) {
      return false;
    }
    try {
      return fs.propsBlocking(path.toString()).isDirectory();
    } catch (Exception e) {
      return false;
    }
  }

  private boolean isRegularFile(Path path) {
    if (!fs.existsBlocking(path.toString())) {
      return false;
    }
    try {
      return fs.propsBlocking(path.toString()).isRegularFile();
    } catch (Exception e) {
      return false;
    }
  }

  private byte[] readAllBytes(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  private PageHistoryEntry parseLogLine(String line) {
    if (line == null || line.isEmpty()) {
      return null;
    }
    String[] parts = line.split("\\|", 5);
    if (parts.length < 5) {
      return null;
    }
    String commitId = parts[0];
    String author = parts[1];
    String authorEmail = parts[2];
    String timestampRaw = parts[3];
    String message = parts[4];
    Instant timestamp;
    try {
      timestamp = Instant.parse(timestampRaw);
    } catch (Exception e) {
      timestamp = Instant.EPOCH;
    }
    return new PageHistoryEntry(commitId, author, authorEmail, message, timestamp);
  }

  private GitIdentity resolveAuthor(PageWriteRequest req) {
    if (req != null && (req.authorName() != null || req.authorEmail() != null)) {
      return new GitIdentity(req.authorName(), req.authorEmail());
    }
    return GitIdentityHolder.current();
  }

  enum MergeStrategy {
    FAST_FORWARD,
    MERGE_COMMIT,
    REBASE,
    SQUASH,
    OURS,
    THEIRS;

    static MergeStrategy fromConfig() {
      String raw = readString("docstore.git.mergeStrategy", "FITNESSE_GIT_MERGE_STRATEGY");
      if (raw == null || raw.isEmpty()) {
        return FAST_FORWARD;
      }
      String normalized = raw.trim().replace("-", "_").replace(" ", "_").toUpperCase();
      try {
        return MergeStrategy.valueOf(normalized);
      } catch (IllegalArgumentException e) {
        return FAST_FORWARD;
      }
    }

    private static String readString(String propertyKey, String envKey) {
      String value = System.getProperty(propertyKey);
      if (value == null || value.isEmpty()) {
        value = System.getenv(envKey);
      }
      return value;
    }
  }
}
