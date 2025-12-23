package fitnesse.ai;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines a single workflow node with prompt, tool, assertions, and layout metadata.
 */
public final class AiWorkflowNode {
  private final String id;
  private final String prompt;
  private final String tool;
  private final String role;
  private final boolean reflection;
  private final String reflectionPrompt;
  private final Map<String, String> params;
  private final String expectedContains;
  private final int positionX;
  private final int positionY;

  public AiWorkflowNode(String id, String prompt, String tool, String role, boolean reflection, String reflectionPrompt,
                        Map<String, String> params, String expectedContains, int positionX, int positionY) {
    this.id = id;
    this.prompt = prompt;
    this.tool = tool;
    this.role = role;
    this.reflection = reflection;
    this.reflectionPrompt = reflectionPrompt;
    this.params = params == null ? new HashMap<>() : new HashMap<>(params);
    this.expectedContains = expectedContains;
    this.positionX = positionX;
    this.positionY = positionY;
  }

  public String id() {
    return id;
  }

  public String prompt() {
    return prompt;
  }

  public String tool() {
    return tool;
  }

  public String role() {
    return role;
  }

  public boolean reflection() {
    return reflection;
  }

  public String reflectionPrompt() {
    return reflectionPrompt;
  }

  public Map<String, String> params() {
    return new HashMap<>(params);
  }

  public String expectedContains() {
    return expectedContains;
  }

  public int positionX() {
    return positionX;
  }

  public int positionY() {
    return positionY;
  }

  public JsonObject toJson() {
    JsonObject paramsJson = new JsonObject();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      paramsJson.put(entry.getKey(), entry.getValue());
    }
    return new JsonObject()
      .put("id", id)
      .put("prompt", prompt)
      .put("tool", tool)
      .put("role", role == null ? "" : role)
      .put("reflection", reflection)
      .put("reflectionPrompt", reflectionPrompt == null ? "" : reflectionPrompt)
      .put("params", paramsJson)
      .put("expectedContains", expectedContains == null ? "" : expectedContains)
      .put("x", positionX)
      .put("y", positionY);
  }

  public static AiWorkflowNode fromJson(JsonObject json) {
    if (json == null) {
      return null;
    }
    String id = json.getString("id", "");
    String prompt = json.getString("prompt", "");
    String tool = json.getString("tool", "assist");
    String role = json.getString("role", "");
    boolean reflection = json.getBoolean("reflection", false);
    String reflectionPrompt = json.getString("reflectionPrompt", "");
    JsonObject params = json.getJsonObject("params", new JsonObject());
    Map<String, String> map = new HashMap<>();
    for (String key : params.fieldNames()) {
      map.put(key, params.getString(key, ""));
    }
    String expectedContains = json.getString("expectedContains", "");
    int x = json.getInteger("x", 40);
    int y = json.getInteger("y", 40);
    return new AiWorkflowNode(id, prompt, tool, role, reflection, reflectionPrompt, map, expectedContains, x, y);
  }
}
