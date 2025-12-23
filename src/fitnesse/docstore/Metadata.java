package fitnesse.docstore;

public final class Metadata {
  private final String contentType;
  private final long size;

  public Metadata(String contentType, long size) {
    this.contentType = contentType;
    this.size = size;
  }

  public String contentType() {
    return contentType;
  }

  public long size() {
    return size;
  }
}
