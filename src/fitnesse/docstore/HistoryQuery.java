package fitnesse.docstore;

public final class HistoryQuery {
  private final int limit;

  public HistoryQuery(int limit) {
    this.limit = limit;
  }

  public int limit() {
    return limit;
  }

  public static HistoryQuery defaultQuery() {
    return new HistoryQuery(50);
  }
}
