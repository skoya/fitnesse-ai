package fitnesse.docstore;

import java.util.Collections;
import java.util.List;

public final class PageHistory {
  private final List<PageHistoryEntry> entries;

  public PageHistory(List<PageHistoryEntry> entries) {
    this.entries = entries == null ? Collections.emptyList() : entries;
  }

  public List<PageHistoryEntry> entries() {
    return entries;
  }
}
