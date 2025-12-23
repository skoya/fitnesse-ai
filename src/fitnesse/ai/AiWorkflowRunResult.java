package fitnesse.ai;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures workflow execution results.
 */
public final class AiWorkflowRunResult {
  public static final class NodeResult {
    private final String id;
    private final String output;
    private final String rawOutput;
    private final String reflectionOutput;
    private final String expectedContains;
    private final boolean passed;

    public NodeResult(String id, String output, String rawOutput, String reflectionOutput, String expectedContains, boolean passed) {
      this.id = id;
      this.output = output;
      this.rawOutput = rawOutput;
      this.reflectionOutput = reflectionOutput;
      this.expectedContains = expectedContains;
      this.passed = passed;
    }

    public JsonObject toJson() {
      return new JsonObject()
        .put("id", id)
        .put("output", output)
        .put("rawOutput", rawOutput == null ? "" : rawOutput)
        .put("reflectionOutput", reflectionOutput == null ? "" : reflectionOutput)
        .put("expectedContains", expectedContains == null ? "" : expectedContains)
        .put("passed", passed);
    }
  }

  private final List<NodeResult> nodes;

  public AiWorkflowRunResult(List<NodeResult> nodes) {
    this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
  }

  public JsonObject toJson() {
    JsonArray array = new JsonArray();
    boolean passed = true;
    for (NodeResult node : nodes) {
      array.add(node.toJson());
      if (!node.passed) {
        passed = false;
      }
    }
    return new JsonObject()
      .put("passed", passed)
      .put("nodes", array);
  }
}
