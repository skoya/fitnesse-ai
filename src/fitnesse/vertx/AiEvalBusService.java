package fitnesse.vertx;

import fitnesse.ai.AiEvalRequest;
import fitnesse.ai.AiEvalResult;
import fitnesse.ai.AiEvalService;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

import java.time.Instant;

/**
 * EventBus facade for AI eval execution.
 */
final class AiEvalBusService {
  static final String ADDRESS_EVAL = "fitnesse.ai.eval";

  private final Vertx vertx;
  private final AiEvalService service;

  AiEvalBusService(Vertx vertx, AiEvalService service) {
    this.vertx = vertx;
    this.service = service;
  }

  void register(EventBus bus) {
    bus.consumer(ADDRESS_EVAL, message -> {
      JsonObject payload = (JsonObject) message.body();
      String prompt = payload.getString("prompt", "");
      String expected = payload.getString("expectedContains", "");
      if (prompt.isEmpty()) {
        message.fail(400, "prompt is required");
        return;
      }
      AiEvalRequest request = new AiEvalRequest(prompt, expected, Instant.now());
      service.evaluate(request).onSuccess(result -> message.reply(toJson(result)))
        .onFailure(error -> message.fail(500, error.getMessage()));
    });
  }

  private JsonObject toJson(AiEvalResult result) {
    return new JsonObject()
      .put("passed", result.passed())
      .put("details", result.details())
      .put("timestamp", result.timestamp().toString());
  }
}
