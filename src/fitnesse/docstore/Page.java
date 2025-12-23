package fitnesse.docstore;

public final class Page {
  private final PageRef ref;
  private final String content;
  private final String propertiesXml;

  public Page(PageRef ref, String content, String propertiesXml) {
    this.ref = ref;
    this.content = content;
    this.propertiesXml = propertiesXml;
  }

  public PageRef ref() {
    return ref;
  }

  public String content() {
    return content;
  }

  public String propertiesXml() {
    return propertiesXml;
  }
}
