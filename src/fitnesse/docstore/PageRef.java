package fitnesse.docstore;

public final class PageRef {
  private final String wikiPath;

  public PageRef(String wikiPath) {
    this.wikiPath = wikiPath;
  }

  public String wikiPath() {
    return wikiPath;
  }
}
