package fitnesse.vertx;

import fitnesse.docstore.GitDocStore;
import fitnesse.docstore.PageRef;
import fitnesse.docstore.PageWriteRequest;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import java.nio.file.Files;
import java.nio.file.Path;

@ExtendWith(VertxExtension.class)
public class GitBusServiceTest {
  @TempDir
  public Path tempDir;

  @Test
  public void historyDiffRevertFlow(Vertx vertx, VertxTestContext testContext) throws Exception {
    Path repoRoot = Files.createDirectory(tempDir.resolve("repo"));
    GitDocStore store = new GitDocStore(repoRoot);
    PageRef ref = new PageRef("FrontPage");
    store.writePage(ref, new PageWriteRequest("alpha", "<properties/>"));
    store.writePage(ref, new PageWriteRequest("beta", "<properties/>"));

    GitBusService service = new GitBusService(vertx, repoRoot);
    service.register(vertx.eventBus());

    vertx.eventBus().request(GitBusService.ADDRESS_HISTORY,
      new JsonObject().put("path", "FrontPage").put("limit", 10), testContext.succeeding(historyAr -> {
        JsonArray entries = ((JsonObject) historyAr.body()).getJsonArray("entries");
        testContext.verify(() ->
          org.junit.jupiter.api.Assertions.assertTrue(entries.size() >= 2));
        String latest = entries.getJsonObject(0).getString("commitId");
        String earliest = entries.getJsonObject(entries.size() - 1).getString("commitId");

        vertx.eventBus().request(GitBusService.ADDRESS_DIFF,
          new JsonObject().put("path", "FrontPage").put("commitId", latest), testContext.succeeding(diffAr -> {
            JsonObject diffBody = (JsonObject) diffAr.body();
            testContext.verify(() ->
              org.junit.jupiter.api.Assertions.assertNotNull(diffBody.getString("diff")));
            vertx.eventBus().request(GitBusService.ADDRESS_REVERT,
              new JsonObject().put("path", "FrontPage").put("commitId", earliest), testContext.succeeding(revertAr -> {
                String content = store.readPage(ref).content();
                testContext.verify(() ->
                  org.junit.jupiter.api.Assertions.assertEquals("alpha", content));
                testContext.completeNow();
              }));
          }));
      }));
  }
}
