package fitnesse.vertx;

import fitnesse.ai.AiEvalService;
import fitnesse.ai.EchoAiProvider;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class AiEvalBusServiceTest {
  @Test
  public void evaluatesPrompt(Vertx vertx, VertxTestContext testContext) {
    AiEvalService service = new AiEvalService(new EchoAiProvider());
    AiEvalBusService busService = new AiEvalBusService(vertx, service);
    busService.register(vertx.eventBus());

    JsonObject payload = new JsonObject()
      .put("prompt", "Hello")
      .put("expectedContains", "Hello");
    vertx.eventBus().request(AiEvalBusService.ADDRESS_EVAL, payload, testContext.succeeding(ar -> {
      JsonObject body = (JsonObject) ar.body();
      testContext.verify(() ->
        org.junit.jupiter.api.Assertions.assertTrue(body.getBoolean("passed")));
      testContext.completeNow();
    }));
  }
}
