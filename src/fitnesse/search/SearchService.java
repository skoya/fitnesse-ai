package fitnesse.search;

import fitnesse.components.TraversalListener;
import fitnesse.wiki.PageData;
import fitnesse.wiki.PageType;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.search.RegularExpressionWikiPageFinder;
import fitnesse.wiki.search.TitleWikiPageFinder;
import fitnesse.wiki.search.WikiPageFinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.LITERAL;

/**
 * Provides in-memory search over the current wiki tree.
 */
public final class SearchService {
  /**
   * Search scope hint.
   */
  public enum Mode {
    TITLE,
    CONTENT
  }

  /**
   * Filters search results by page type.
   */
  public enum PageTypeFilter {
    ANY,
    SUITE,
    TEST
  }

  private final WikiPage root;

  /**
   * Creates a new SearchService anchored to the wiki root.
   */
  public SearchService(WikiPage root) {
    this.root = root;
  }

  /**
   * Runs a search against the wiki tree with optional filters.
   */
  public List<SearchResult> search(String query, Mode mode, int limit, int offset, List<String> tags, PageTypeFilter pageTypeFilter) {
    List<SearchResult> results = new ArrayList<>();
    if (query == null || query.isEmpty()) {
      return results;
    }
    int max = limit <= 0 ? 50 : limit;
    int skip = Math.max(0, offset);
    int[] seen = new int[] { 0 };
    TraversalListener<WikiPage> listener = page -> {
      if (!matchesFilters(page, tags, pageTypeFilter)) {
        return;
      }
      if (seen[0] < skip) {
        seen[0]++;
        return;
      }
      if (results.size() >= max) {
        return;
      }
      String path = page.getFullPath().toString();
      String snippet = buildSnippet(page, query, mode);
      results.add(new SearchResult(path, snippet));
    };

    WikiPageFinder finder = mode == Mode.TITLE
      ? new TitleWikiPageFinder(query, listener)
      : new RegularExpressionWikiPageFinder(Pattern.compile(query, CASE_INSENSITIVE + LITERAL), listener);

    finder.search(root);
    return results;
  }

  private boolean matchesFilters(WikiPage page, List<String> tags, PageTypeFilter pageTypeFilter) {
    if (pageTypeFilter != null && pageTypeFilter != PageTypeFilter.ANY) {
      PageType pageType = PageType.fromWikiPage(page);
      if (pageTypeFilter == PageTypeFilter.SUITE && pageType != PageType.SUITE) {
        return false;
      }
      if (pageTypeFilter == PageTypeFilter.TEST && pageType != PageType.TEST) {
        return false;
      }
    }
    if (tags == null || tags.isEmpty()) {
      return true;
    }
    PageData data = page.getData();
    String rawTags = data == null ? "" : data.getAttribute(PageData.PropertySUITES);
    if (rawTags == null || rawTags.isEmpty()) {
      return false;
    }
    Set<String> pageTags = new HashSet<>();
    for (String tag : rawTags.split(",")) {
      String normalized = tag.trim().toLowerCase();
      if (!normalized.isEmpty()) {
        pageTags.add(normalized);
      }
    }
    for (String tag : tags) {
      if (!pageTags.contains(tag.toLowerCase())) {
        return false;
      }
    }
    return true;
  }

  private String buildSnippet(WikiPage page, String query, Mode mode) {
    if (mode == Mode.TITLE) {
      return page.getName();
    }
    PageData data = page.getData();
    String content = data == null ? "" : data.getContent();
    if (content == null) {
      return "";
    }
    String lower = content.toLowerCase();
    String needle = query.toLowerCase();
    int index = lower.indexOf(needle);
    if (index < 0) {
      return content.length() > 200 ? content.substring(0, 200) : content;
    }
    int start = Math.max(0, index - 80);
    int end = Math.min(content.length(), index + needle.length() + 80);
    return content.substring(start, end);
  }
}
