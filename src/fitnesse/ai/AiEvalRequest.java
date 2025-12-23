package fitnesse.ai;

import java.time.Instant;

/**
 * Defines an evaluation request for an AI response.
 */
public final class AiEvalRequest {
  private final String prompt;
  private final String expectedContains;
  private final Instant timestamp;

  public AiEvalRequest(String prompt, String expectedContains, Instant timestamp) {
    this.prompt = prompt;
    this.expectedContains = expectedContains;
    this.timestamp = timestamp;
  }

  public String prompt() {
    return prompt;
  }

  public String expectedContains() {
    return expectedContains;
  }

  public Instant timestamp() {
    return timestamp;
  }
}
