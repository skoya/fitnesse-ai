package fitnesse.docstore;

public final class PageWriteRequest {
  private final String content;
  private final String propertiesXml;
  private final String expectedVersion;
  private final String authorName;
  private final String authorEmail;

  public PageWriteRequest(String content, String propertiesXml) {
    this(content, propertiesXml, null);
  }

  public PageWriteRequest(String content, String propertiesXml, String expectedVersion) {
    this(content, propertiesXml, expectedVersion, null, null);
  }

  public PageWriteRequest(String content, String propertiesXml, String expectedVersion, String authorName, String authorEmail) {
    this.content = content;
    this.propertiesXml = propertiesXml;
    this.expectedVersion = expectedVersion;
    this.authorName = authorName;
    this.authorEmail = authorEmail;
  }

  public String content() {
    return content;
  }

  public String propertiesXml() {
    return propertiesXml;
  }

  public String expectedVersion() {
    return expectedVersion;
  }

  public String authorName() {
    return authorName;
  }

  public String authorEmail() {
    return authorEmail;
  }
}
