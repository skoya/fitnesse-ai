package fitnesse.ai;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a graph of workflow nodes with metadata.
 */
public final class AiWorkflow {
  private final String id;
  private final String name;
  private final List<AiWorkflowNode> nodes;
  private final JsonArray edges;

  public AiWorkflow(String id, String name, List<AiWorkflowNode> nodes, JsonArray edges) {
    this.id = id;
    this.name = name;
    this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
    this.edges = edges == null ? new JsonArray() : edges.copy();
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public List<AiWorkflowNode> nodes() {
    return new ArrayList<>(nodes);
  }

  public JsonArray edges() {
    return edges.copy();
  }

  public JsonObject toJson() {
    JsonArray nodeArray = new JsonArray();
    for (AiWorkflowNode node : nodes) {
      nodeArray.add(node.toJson());
    }
    return new JsonObject()
      .put("id", id)
      .put("name", name)
      .put("nodes", nodeArray)
      .put("edges", edges);
  }

  public static AiWorkflow fromJson(JsonObject json) {
    if (json == null) {
      return null;
    }
    String id = json.getString("id", "");
    String name = json.getString("name", "");
    JsonArray nodesJson = json.getJsonArray("nodes", new JsonArray());
    List<AiWorkflowNode> nodes = new ArrayList<>();
    for (int i = 0; i < nodesJson.size(); i++) {
      AiWorkflowNode node = AiWorkflowNode.fromJson(nodesJson.getJsonObject(i));
      if (node != null) {
        nodes.add(node);
      }
    }
    JsonArray edges = json.getJsonArray("edges", new JsonArray());
    return new AiWorkflow(id, name, nodes, edges);
  }
}
