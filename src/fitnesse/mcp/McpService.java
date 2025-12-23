package fitnesse.mcp;

import fitnesse.components.TraversalListener;
import fitnesse.search.SearchResult;
import fitnesse.search.SearchService;
import fitnesse.wiki.NoPruningStrategy;
import fitnesse.wiki.PageData;
import fitnesse.wiki.PathParser;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPageProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides MCP-friendly access to wiki pages and search results.
 */
public final class McpService {
  /**
   * Lightweight page listing entry.
   */
  public static final class PageSummary {
    private final String name;
    private final String path;

    public PageSummary(String name, String path) {
      this.name = name;
      this.path = path;
    }

    public String name() {
      return name;
    }

    public String path() {
      return path;
    }
  }

  /**
   * Full page payload returned by MCP read operations.
   */
  public static final class PageDetails {
    private final String name;
    private final String path;
    private final String content;
    private final String lastModified;

    public PageDetails(String name, String path, String content, String lastModified) {
      this.name = name;
      this.path = path;
      this.content = content;
      this.lastModified = lastModified;
    }

    public String name() {
      return name;
    }

    public String path() {
      return path;
    }

    public String content() {
      return content;
    }

    public String lastModified() {
      return lastModified;
    }
  }

  /**
   * Collection returned for paged list operations.
   */
  public static final class PageListing {
    private final List<PageSummary> pages;
    private final int total;
    private final int limit;
    private final int offset;

    public PageListing(List<PageSummary> pages, int total, int limit, int offset) {
      this.pages = pages;
      this.total = total;
      this.limit = limit;
      this.offset = offset;
    }

    public List<PageSummary> pages() {
      return pages;
    }

    public int total() {
      return total;
    }

    public int limit() {
      return limit;
    }

    public int offset() {
      return offset;
    }
  }

  /**
   * Collection returned for paged search operations.
   */
  public static final class SearchListing {
    private final List<SearchResult> results;
    private final int limit;
    private final int offset;

    public SearchListing(List<SearchResult> results, int limit, int offset) {
      this.results = results;
      this.limit = limit;
      this.offset = offset;
    }

    public List<SearchResult> results() {
      return results;
    }

    public int limit() {
      return limit;
    }

    public int offset() {
      return offset;
    }
  }

  private final WikiPage root;
  private final SearchService searchService;

  public McpService(WikiPage root) {
    this.root = root;
    this.searchService = new SearchService(root);
  }

  /**
   * Lists pages in the wiki tree with pagination.
   */
  public PageListing listPages(int limit, int offset) {
    int safeLimit = limit <= 0 ? 50 : limit;
    int safeOffset = Math.max(0, offset);
    List<PageSummary> pages = new ArrayList<>();
    root.getPageCrawler().traverse(new TraversalListener<WikiPage>() {
      @Override
      public void process(WikiPage page) {
        if (page.isRoot()) {
          return;
        }
        String path = page.getFullPath().toString();
        pages.add(new PageSummary(page.getName(), path));
      }
    }, new NoPruningStrategy());
    int total = pages.size();
    if (safeOffset >= total) {
      return new PageListing(Collections.emptyList(), total, safeLimit, safeOffset);
    }
    int end = Math.min(total, safeOffset + safeLimit);
    return new PageListing(pages.subList(safeOffset, end), total, safeLimit, safeOffset);
  }

  /**
   * Reads a page by path and returns content plus basic metadata.
   */
  public PageDetails readPage(String rawPath) {
    String normalized = normalizePath(rawPath);
    WikiPage page = root.getPageCrawler().getPage(PathParser.parse(normalized));
    if (page == null) {
      return null;
    }
    PageData data = page.getData();
    String content = data == null ? "" : data.getContent();
    String lastModified = data == null ? "" : data.getAttribute(WikiPageProperty.LAST_MODIFIED);
    return new PageDetails(page.getName(), page.getFullPath().toString(), content, lastModified);
  }

  /**
   * Searches the wiki tree using content matches.
   */
  public SearchListing search(String query, int limit, int offset) {
    int safeLimit = limit <= 0 ? 50 : limit;
    int safeOffset = Math.max(0, offset);
    List<SearchResult> results = searchService.search(query, SearchService.Mode.CONTENT, safeLimit, safeOffset,
      Collections.emptyList(), SearchService.PageTypeFilter.ANY);
    return new SearchListing(results, safeLimit, safeOffset);
  }

  private String normalizePath(String rawPath) {
    if (rawPath == null || rawPath.isEmpty()) {
      return "FrontPage";
    }
    if (rawPath.startsWith("/")) {
      return rawPath.substring(1);
    }
    return rawPath;
  }
}
