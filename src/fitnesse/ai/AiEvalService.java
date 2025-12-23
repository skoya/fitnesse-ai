package fitnesse.ai;

import io.vertx.core.Future;

import java.time.Instant;

/**
 * Runs lightweight evals against AI responses.
 */
public final class AiEvalService {
  private final AiProvider provider;

  public AiEvalService(AiProvider provider) {
    this.provider = provider;
  }

  /**
   * Executes a simple contains-based evaluation.
   */
  public Future<AiEvalResult> evaluate(AiEvalRequest request) {
    AiRequest aiRequest = new AiRequest(request.prompt(), java.util.List.of(), "assist", java.util.Map.of(), "eval", request.timestamp());
    return provider.generate(aiRequest)
      .map(response -> buildResult(response, request));
  }

  private AiEvalResult buildResult(AiResponse response, AiEvalRequest request) {
    String expected = request.expectedContains();
    boolean passed = expected == null || expected.isEmpty() || response.response().contains(expected);
    String details = passed ? "PASS" : "Expected to contain: " + expected;
    return new AiEvalResult(passed, details, Instant.now());
  }
}
