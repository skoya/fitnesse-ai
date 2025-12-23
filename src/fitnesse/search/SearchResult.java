package fitnesse.search;

/**
 * Represents a search hit with a page path and short snippet.
 */
public final class SearchResult {
  private final String path;
  private final String snippet;

  public SearchResult(String path, String snippet) {
    this.path = path;
    this.snippet = snippet;
  }

  /**
   * Returns the full page path.
   */
  public String path() {
    return path;
  }

  /**
   * Returns a content snippet to display in UI or API clients.
   */
  public String snippet() {
    return snippet;
  }
}
