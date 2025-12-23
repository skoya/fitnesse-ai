package fitnesse.ai;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.time.Instant;

/**
 * Minimal OpenAI chat-completions provider.
 */
public final class OpenAiProvider implements AiProvider {
  private final WebClient client;
  private final String apiKey;
  private final String model;

  public OpenAiProvider(Vertx vertx, String apiKey, String model) {
    this.apiKey = apiKey;
    this.model = model;
    this.client = WebClient.create(vertx);
  }

  @Override
  public Future<AiResponse> generate(AiRequest request) {
    if (apiKey == null || apiKey.isEmpty()) {
      return Future.failedFuture("OPENAI_API_KEY is required");
    }
    JsonObject body = new JsonObject();
    body.put("model", model == null || model.isEmpty() ? "gpt-4o-mini" : model);
    JsonArray messages = new JsonArray();
    JsonObject user = new JsonObject();
    user.put("role", "user");
    user.put("content", request.prompt());
    messages.add(user);
    body.put("messages", messages);

    Promise<AiResponse> promise = Promise.promise();
    client
      .postAbs("https://api.openai.com/v1/chat/completions")
      .putHeader("Authorization", "Bearer " + apiKey)
      .putHeader("Content-Type", "application/json")
      .timeout(30_000)
      .sendJsonObject(body)
      .onFailure(promise::fail)
      .onSuccess(resp -> handleResponse(resp, request, promise));
    return promise.future();
  }

  private void handleResponse(HttpResponse<io.vertx.core.buffer.Buffer> response, AiRequest request,
                              Promise<AiResponse> promise) {
    if (response.statusCode() >= 300) {
      promise.fail("OpenAI error: " + response.statusCode() + " " + response.bodyAsString());
      return;
    }
    JsonObject json = response.bodyAsJsonObject();
    JsonArray choices = json.getJsonArray("choices", new JsonArray());
    if (choices.isEmpty()) {
      promise.fail("OpenAI: no choices returned");
      return;
    }
    String content = choices.getJsonObject(0).getJsonObject("message").getString("content");
    promise.complete(new AiResponse(content, request.conversationId(), Instant.now()));
  }
}
