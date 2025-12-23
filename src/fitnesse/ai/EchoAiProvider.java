package fitnesse.ai;

import io.vertx.core.Future;

import java.time.Instant;

/**
 * Placeholder AI provider that echoes the prompt for local testing.
 */
public final class EchoAiProvider implements AiProvider {
  @Override
  public Future<AiResponse> generate(AiRequest request) {
    String response = "Echo: " + request.prompt();
    return Future.succeededFuture(new AiResponse(response, request.conversationId(), Instant.now()));
  }
}
