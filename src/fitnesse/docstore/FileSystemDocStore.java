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
import java.util.ArrayList;
import java.util.List;

public final class FileSystemDocStore implements DocStore {
  private static final String CONTENT_FILE = "content.txt";
  private static final String PROPERTIES_FILE = "properties.xml";
  private static final String ATTACHMENTS_DIR = "files";

  private final Vertx vertx;
  private final FileSystem fs;
  private final Path root;

  public FileSystemDocStore(Path root) {
    this(Vertx.vertx(), root);
  }

  public FileSystemDocStore(Vertx vertx, Path root) {
    this.vertx = vertx;
    this.fs = vertx.fileSystem();
    this.root = root;
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
    try {
      fs.mkdirsBlocking(pageDir.toString());
      fs.writeFileBlocking(pageDir.resolve(CONTENT_FILE).toString(), Buffer.buffer(safe(req.content()), StandardCharsets.UTF_8.name()));
      if (req.propertiesXml() != null) {
        fs.writeFileBlocking(pageDir.resolve(PROPERTIES_FILE).toString(),
          Buffer.buffer(req.propertiesXml(), StandardCharsets.UTF_8.name()));
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write page " + ref.wikiPath(), e);
    }
  }

  @Override
  public List<PageRef> listChildren(PageRef ref) {
    Path pageDir = pageDir(ref);
    List<PageRef> children = new ArrayList<>();
    if (!fs.existsBlocking(pageDir.toString()) || !fs.propsBlocking(pageDir.toString()).isDirectory()) {
      return children;
    }
    try {
      for (String child : fs.readDirBlocking(pageDir.toString())) {
        if (fs.propsBlocking(child).isDirectory()) {
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
  }

  @Override
  public List<AttachmentRef> listAttachments(PageRef ref) {
    Path attachmentsDir = pageDir(ref).resolve(ATTACHMENTS_DIR);
    List<AttachmentRef> attachments = new ArrayList<>();
    if (!fs.existsBlocking(attachmentsDir.toString()) || !fs.propsBlocking(attachmentsDir.toString()).isDirectory()) {
      return attachments;
    }
    try {
      for (String child : fs.readDirBlocking(attachmentsDir.toString())) {
        if (fs.propsBlocking(child).isRegularFile()) {
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
  }

  @Override
  public PageHistory history(PageRef ref, HistoryQuery q) {
    throw new UnsupportedOperationException("History is not implemented for FileSystemDocStore yet.");
  }

  private Path pageDir(PageRef ref) {
    String[] parts = ref.wikiPath().split("/");
    Path current = root;
    for (String part : parts) {
      if (!part.isEmpty()) {
        current = current.resolve(part);
      }
    }
    return current;
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

  private byte[] readAllBytes(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }
}
