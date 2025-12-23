package fitnesse.docstore;

public final class PageProperties {
  private final String xml;

  public PageProperties(String xml) {
    this.xml = xml;
  }

  public String xml() {
    return xml;
  }
}
