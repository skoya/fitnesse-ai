package fitnesse.vertx;

import fitnesse.ai.AiAssistantService;
import fitnesse.ai.AiHistoryStore;
import fitnesse.ai.EchoAiProvider;
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
public class AiBusServiceTest {
  @TempDir
  public Path tempDir;

  @Test
  public void persistsHistory(Vertx vertx, VertxTestContext testContext) throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("repo"));
    AiHistoryStore historyStore = new AiHistoryStore(vertx, root);
    AiAssistantService service = new AiAssistantService(new EchoAiProvider(), historyStore);
    AiBusService busService = new AiBusService(vertx, service);
    busService.register(vertx.eventBus());

    JsonObject payload = new JsonObject()
      .put("prompt", "Hello")
      .put("conversationId", "c-1")
      .put("grounding", new JsonArray().add("doc-1"));
    vertx.eventBus().request(AiBusService.ADDRESS_ASSIST, payload, testContext.succeeding(ar -> {
      JsonObject body = (JsonObject) ar.body();
      testContext.verify(() -> {
        org.junit.jupiter.api.Assertions.assertEquals("c-1", body.getString("conversationId"));
        org.junit.jupiter.api.Assertions.assertTrue(body.getString("response").contains("Hello"));
      });

      try {
        Path history = root.resolve(".fitnesse").resolve("ai").resolve("history.jsonl");
        testContext.verify(() -> {
          org.junit.jupiter.api.Assertions.assertTrue(Files.exists(history));
          org.junit.jupiter.api.Assertions.assertTrue(Files.readString(history).contains("Hello"));
        });
        testContext.completeNow();
      } catch (Exception e) {
        testContext.failNow(e);
      }
    }));
  }

  @Test
  public void testGenerationToolStub(Vertx vertx, VertxTestContext testContext) throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("repo2"));
    AiHistoryStore historyStore = new AiHistoryStore(vertx, root);
    AiAssistantService service = new AiAssistantService(new EchoAiProvider(), historyStore);
    AiBusService busService = new AiBusService(vertx, service);
    busService.register(vertx.eventBus());

    JsonObject payload = new JsonObject()
      .put("prompt", "Generate a test")
      .put("tool", "test-gen")
      .put("params", new JsonObject().put("pagePath", "SuiteOne.TestOne").put("fixture", "MyFixture"));
    vertx.eventBus().request(AiBusService.ADDRESS_ASSIST, payload, testContext.succeeding(ar -> {
      JsonObject body = (JsonObject) ar.body();
      testContext.verify(() ->
        org.junit.jupiter.api.Assertions.assertTrue(body.getString("response").contains("Test generation stub")));
      testContext.completeNow();
    }));
  }
}
