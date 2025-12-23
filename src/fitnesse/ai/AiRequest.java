package fitnesse.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents an AI assist request with optional conversation context.
 */
public final class AiRequest {
  private final String prompt;
  private final List<String> grounding;
  private final String tool;
  private final Map<String, String> parameters;
  private final String conversationId;
  private final Instant timestamp;

  public AiRequest(String prompt, List<String> grounding, String tool, Map<String, String> parameters,
                   String conversationId, Instant timestamp) {
    this.prompt = prompt;
    this.grounding = grounding;
    this.tool = tool;
    this.parameters = parameters;
    this.conversationId = conversationId;
    this.timestamp = timestamp;
  }

  public String prompt() {
    return prompt;
  }

  public List<String> grounding() {
    return grounding;
  }

  public String tool() {
    return tool;
  }

  public Map<String, String> parameters() {
    return parameters;
  }

  public String conversationId() {
    return conversationId;
  }

  public Instant timestamp() {
    return timestamp;
  }
}
