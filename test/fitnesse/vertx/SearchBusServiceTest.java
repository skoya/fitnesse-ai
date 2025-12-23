package fitnesse.vertx;

import fitnesse.ContextConfigurator;
import fitnesse.FitNesseContext;
import fitnesse.search.SearchService;
import fitnesse.wiki.PageData;
import fitnesse.wiki.PathParser;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPageUtil;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class SearchBusServiceTest {
  @TempDir
  public java.nio.file.Path tempDir;

  @Test
  public void filtersAndPaging(Vertx vertx, VertxTestContext testContext) throws Exception {
    FitNesseContext fitNesseContext = ContextConfigurator.systemDefaults()
      .withRootPath(java.nio.file.Files.createDirectory(tempDir.resolve("root")).toString())
      .withRootDirectoryName("FitNesseRoot")
      .makeFitNesseContext();

    WikiPage root = fitNesseContext.getRootPage();
    WikiPage suitePage = WikiPageUtil.addPage(root, PathParser.parse("SuiteOne"), "alpha search");
    PageData suiteData = suitePage.getData();
    suiteData.setAttribute("Suite");
    suiteData.setAttribute(PageData.PropertySUITES, "fast,smoke");
    suitePage.commit(suiteData);

    WikiPage testPage = WikiPageUtil.addPage(root, PathParser.parse("TestOne"), "alpha search");
    PageData testData = testPage.getData();
    testData.setAttribute("Test");
    testData.setAttribute(PageData.PropertySUITES, "slow");
    testPage.commit(testData);

    SearchService searchService = new SearchService(root);
    SearchBusService busService = new SearchBusService(vertx, searchService);
    busService.register(vertx.eventBus());

    vertx.eventBus().request(SearchBusService.ADDRESS_SEARCH, new JsonObject()
      .put("query", "alpha")
      .put("type", "content")
      .put("tags", "smoke")
      .put("pageType", "suite")
      .put("limit", 1)
      .put("offset", 0))
      .onComplete(testContext.succeeding(message -> {
        JsonObject body = (JsonObject) message.body();
        JsonArray results = body.getJsonArray("results");
        testContext.verify(() -> {
          org.junit.jupiter.api.Assertions.assertEquals(1, results.size());
          org.junit.jupiter.api.Assertions.assertEquals("SuiteOne", results.getJsonObject(0).getString("path"));
          org.junit.jupiter.api.Assertions.assertEquals(1, body.getInteger("count", 0).intValue());
        });
        testContext.completeNow();
      }));
  }
}
