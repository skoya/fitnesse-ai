package fitnesse.vertx;

import fitnesse.ai.AiAssistantService;
import fitnesse.ai.AiHistoryStore;
import fitnesse.ai.AiWorkflow;
import fitnesse.ai.AiWorkflowNode;
import fitnesse.ai.AiWorkflowService;
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
import java.util.List;
import java.util.Map;

@ExtendWith(VertxExtension.class)
public class AiWorkflowBusServiceTest {
  @TempDir
  public Path tempDir;

  @Test
  public void saveRunAndListWorkflow(Vertx vertx, VertxTestContext testContext) throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("repo"));
    AiAssistantService assistantService = new AiAssistantService(new EchoAiProvider(), new AiHistoryStore(vertx, root));
    AiWorkflowService service = new AiWorkflowService(vertx, assistantService, root);
    AiWorkflowBusService busService = new AiWorkflowBusService(service);
    busService.register(vertx.eventBus());

    AiWorkflowNode node = new AiWorkflowNode("n1", "Hello", "assist", "", false, "", Map.of(), "Hello", 40, 40);
    AiWorkflow workflow = new AiWorkflow("wf1", "Workflow 1", List.of(node), new JsonArray());

    vertx.eventBus().request(AiWorkflowBusService.ADDRESS_SAVE, workflow.toJson(), testContext.succeeding(saveAr -> {
      JsonObject payload = new JsonObject().put("workflow", workflow.toJson());
      vertx.eventBus().request(AiWorkflowBusService.ADDRESS_RUN, payload, testContext.succeeding(runAr -> {
        JsonObject runsPayload = new JsonObject().put("id", "wf1").put("limit", 5);
        vertx.eventBus().request(AiWorkflowBusService.ADDRESS_RUNS, runsPayload, testContext.succeeding(runsAr -> {
          JsonObject result = (JsonObject) runsAr.body();
          testContext.verify(() ->
            org.junit.jupiter.api.Assertions.assertTrue(result.getJsonArray("runs").size() >= 1));
          testContext.completeNow();
        }));
      }));
    }));
  }
}
