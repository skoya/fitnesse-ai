package fitnesse.docstore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class GitHistoryService {
  private final Path repoRoot;
  private final GitRepository git;

  public GitHistoryService(Path repoRoot) {
    this.repoRoot = repoRoot;
    this.git = new GitRepository(repoRoot);
  }

  public PageHistory history(PageRef ref, HistoryQuery query) {
    int limit = query == null ? HistoryQuery.defaultQuery().limit() : query.limit();
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

  public String diff(PageRef ref, String commitId) {
    return git.diff(relativePath(ref), commitId);
  }

  public void revert(PageRef ref, String commitId) {
    git.checkout(commitId, relativePath(ref));
    git.commit("wiki: revert " + ref.wikiPath() + " to " + commitId);
  }

  private String relativePath(PageRef ref) {
    return ref.wikiPath().replace("\\", "/");
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
}
