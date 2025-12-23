package fitnesse.ai;

import io.vertx.core.Future;

/**
 * Provides AI completions for assist requests.
 */
public interface AiProvider {
  Future<AiResponse> generate(AiRequest request);
}
