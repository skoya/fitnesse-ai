package fitnesse.ai;

import java.time.Instant;

/**
 * Captures evaluation outcome for an AI response.
 */
public final class AiEvalResult {
  private final boolean passed;
  private final String details;
  private final Instant timestamp;

  public AiEvalResult(boolean passed, String details, Instant timestamp) {
    this.passed = passed;
    this.details = details;
    this.timestamp = timestamp;
  }

  public boolean passed() {
    return passed;
  }

  public String details() {
    return details;
  }

  public Instant timestamp() {
    return timestamp;
  }
}
