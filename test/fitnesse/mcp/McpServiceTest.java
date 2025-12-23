package fitnesse.mcp;

import fitnesse.search.SearchResult;
import fitnesse.wiki.PathParser;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPageUtil;
import fitnesse.wiki.fs.InMemoryPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class McpServiceTest {

  @Test
  public void listsPagesWithPaging() {
    WikiPage root = InMemoryPage.makeRoot("RooT");
    WikiPageUtil.addPage(root, PathParser.parse("FrontPage"), "Front content");
    WikiPageUtil.addPage(root, PathParser.parse("SuiteOne.TestA"), "Test A");

    McpService service = new McpService(root);
    McpService.PageListing listing = service.listPages(2, 0);

    assertThat(listing.total(), is(3));
    assertThat(listing.limit(), is(2));
    assertThat(listing.offset(), is(0));
    assertThat(paths(listing.pages()), hasItem("FrontPage"));
  }

  @Test
  public void readsPageContent() {
    WikiPage root = InMemoryPage.makeRoot("RooT");
    WikiPageUtil.addPage(root, PathParser.parse("FrontPage"), "Front content");

    McpService service = new McpService(root);
    McpService.PageDetails details = service.readPage("FrontPage");

    assertThat(details, notNullValue());
    assertThat(details.content(), containsString("Front content"));
  }

  @Test
  public void searchesPageContent() {
    WikiPage root = InMemoryPage.makeRoot("RooT");
    WikiPageUtil.addPage(root, PathParser.parse("FrontPage"), "Searchable content");

    McpService service = new McpService(root);
    McpService.SearchListing results = service.search("Searchable", 10, 0);

    assertThat(pathsFromResults(results.results()), hasItem("FrontPage"));
  }

  private static List<String> paths(List<McpService.PageSummary> pages) {
    return pages.stream().map(McpService.PageSummary::path).toList();
  }

  private static List<String> pathsFromResults(List<SearchResult> results) {
    return results.stream().map(SearchResult::path).toList();
  }
}
