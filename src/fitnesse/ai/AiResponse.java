package fitnesse.ai;

import java.time.Instant;

/**
 * Represents an AI response with metadata.
 */
public final class AiResponse {
  private final String response;
  private final String conversationId;
  private final Instant timestamp;

  public AiResponse(String response, String conversationId, Instant timestamp) {
    this.response = response;
    this.conversationId = conversationId;
    this.timestamp = timestamp;
  }

  public String response() {
    return response;
  }

  public String conversationId() {
    return conversationId;
  }

  public Instant timestamp() {
    return timestamp;
  }
}
