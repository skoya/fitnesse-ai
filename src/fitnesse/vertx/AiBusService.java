package fitnesse.vertx;

import fitnesse.ai.AiAssistantService;
import fitnesse.ai.AiRequest;
import fitnesse.ai.AiResponse;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EventBus facade for AI assist requests with lightweight shared-data throttling.
 */
final class AiBusService {
  static final String ADDRESS_ASSIST = "fitnesse.ai.assist";
  private static final String CACHE_NAME = "fitnesse.ai.throttle";

  private final Vertx vertx;
  private final AiAssistantService service;
  private final LocalMap<String, Long> throttle;

  AiBusService(Vertx vertx, AiAssistantService service) {
    this.vertx = vertx;
    this.service = service;
    this.throttle = vertx.sharedData().getLocalMap(CACHE_NAME);
  }

  /**
   * Registers the AI assist handler on the EventBus.
   */
  void register(EventBus bus) {
    bus.consumer(ADDRESS_ASSIST, message -> {
      JsonObject payload = (JsonObject) message.body();
      String prompt = payload.getString("prompt", "");
      String tool = payload.getString("tool", "assist");
      String conversationId = payload.getString("conversationId", UUID.randomUUID().toString());
      List<String> grounding = parseGrounding(payload.getJsonArray("grounding"));
      java.util.Map<String, String> parameters = parseParams(payload.getJsonObject("params"));
      if (prompt.isEmpty()) {
        message.fail(400, "prompt is required");
        return;
      }
      if (!allowRequest(conversationId)) {
        message.fail(429, "Too many requests");
        return;
      }

      AiRequest request = AiAssistantService.buildRequest(prompt, grounding, tool, parameters, conversationId);
      service.assist(request).onSuccess(response -> {
        JsonObject body = new JsonObject()
          .put("conversationId", response.conversationId())
          .put("response", response.response())
          .put("timestamp", response.timestamp().toString());
        message.reply(body);
      }).onFailure(error -> message.fail(500, error.getMessage()));
    });
  }

  private boolean allowRequest(String conversationId) {
    long now = System.currentTimeMillis();
    Long last = throttle.get(conversationId);
    if (last != null && now - last < 300) {
      return false;
    }
    throttle.put(conversationId, now);
    return true;
  }

  private List<String> parseGrounding(JsonArray array) {
    List<String> results = new ArrayList<>();
    if (array == null) {
      return results;
    }
    for (int i = 0; i < array.size(); i++) {
      String item = array.getString(i);
      if (item == null) {
        item = "";
      }
      if (!item.isEmpty()) {
        results.add(item);
      }
    }
    return results;
  }

  private java.util.Map<String, String> parseParams(JsonObject object) {
    java.util.Map<String, String> params = new java.util.HashMap<>();
    if (object == null) {
      return params;
    }
    for (String key : object.fieldNames()) {
      String value = object.getString(key, "");
      if (!value.isEmpty()) {
        params.put(key, value);
      }
    }
    return params;
  }
}
