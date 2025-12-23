package fitnesse.ai;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists workflows and executes runs using AiAssistantService.
 */
public final class AiWorkflowService {
  private final Vertx vertx;
  private final AiAssistantService assistantService;
  private final Path workflowDir;

  public AiWorkflowService(Vertx vertx, AiAssistantService assistantService, Path rootDir) {
    this.vertx = vertx;
    this.assistantService = assistantService;
    this.workflowDir = rootDir.resolve(".fitnesse").resolve("ai").resolve("workflows");
  }

  /**
   * Lists stored workflows (id + name only).
   */
  public Future<JsonArray> list() {
    Promise<JsonArray> promise = Promise.promise();
    vertx.fileSystem().mkdirs(workflowDir.toString(), mkdirAr -> {
      if (mkdirAr.failed()) {
        promise.fail(mkdirAr.cause());
        return;
      }
      vertx.fileSystem().readDir(workflowDir.toString(), readAr -> {
        if (readAr.failed()) {
          promise.fail(readAr.cause());
          return;
        }
        List<Future<JsonObject>> reads = new ArrayList<>();
        for (String path : readAr.result()) {
          Promise<JsonObject> itemPromise = Promise.promise();
          vertx.fileSystem().readFile(path, fileAr -> {
            if (fileAr.failed()) {
              itemPromise.complete(new JsonObject());
              return;
            }
            JsonObject json = fileAr.result().toJsonObject();
            itemPromise.complete(new JsonObject()
              .put("id", json.getString("id", ""))
              .put("name", json.getString("name", "")));
          });
          reads.add(itemPromise.future());
        }
        Future.join(new ArrayList<>(reads)).onComplete(done -> {
          JsonArray array = new JsonArray();
          for (Future<JsonObject> future : reads) {
            JsonObject item = future.result();
            if (item != null && !item.getString("id", "").isEmpty()) {
              array.add(item);
            }
          }
          promise.complete(array);
        });
      });
    });
    return promise.future();
  }

  /**
   * Loads a workflow by id.
   */
  public Future<AiWorkflow> load(String id) {
    Promise<AiWorkflow> promise = Promise.promise();
    if (id == null || id.isEmpty()) {
      promise.fail("id required");
      return promise.future();
    }
    Path path = workflowDir.resolve(id + ".json");
    vertx.fileSystem().readFile(path.toString(), ar -> {
      if (ar.failed()) {
        promise.fail(ar.cause());
      } else {
        promise.complete(AiWorkflow.fromJson(ar.result().toJsonObject()));
      }
    });
    return promise.future();
  }

  /**
   * Persists a workflow definition.
   */
  public Future<AiWorkflow> save(AiWorkflow workflow) {
    Promise<AiWorkflow> promise = Promise.promise();
    if (workflow == null || workflow.id() == null || workflow.id().isEmpty()) {
      promise.fail("id required");
      return promise.future();
    }
    vertx.fileSystem().mkdirs(workflowDir.toString(), mkdirAr -> {
      if (mkdirAr.failed()) {
        promise.fail(mkdirAr.cause());
        return;
      }
      Path path = workflowDir.resolve(workflow.id() + ".json");
      Buffer buffer = Buffer.buffer(workflow.toJson().encodePrettily(), StandardCharsets.UTF_8.name());
      OpenOptions options = new OpenOptions().setCreate(true).setWrite(true).setTruncateExisting(true);
      vertx.fileSystem().open(path.toString(), options, openAr -> {
        if (openAr.failed()) {
          promise.fail(openAr.cause());
          return;
        }
        openAr.result().write(buffer, writeAr -> {
          openAr.result().close();
          if (writeAr.failed()) {
            promise.fail(writeAr.cause());
          } else {
            promise.complete(workflow);
          }
        });
      });
    });
    return promise.future();
  }

  /**
   * Executes a workflow in sequence, applying basic assertions.
   */
  public Future<AiWorkflowRunResult> run(AiWorkflow workflow) {
    if (workflow == null) {
      return Future.failedFuture("workflow required");
    }
    List<AiWorkflowNode> ordered = WorkflowOrderer.order(workflow.nodes(), workflow.edges());
    List<AiWorkflowRunResult.NodeResult> results = new ArrayList<>();
    Future<String> chain = Future.succeededFuture("");
    for (AiWorkflowNode node : ordered) {
      chain = chain.compose(prev -> {
        String previous = prev == null ? "" : prev;
        String prompt = node.prompt().replace("{{prev}}", previous);
        if (node.role() != null && !node.role().isEmpty()) {
          prompt = "Role: " + node.role() + "\n" + prompt;
        }
        AiRequest request = AiAssistantService.buildRequest(prompt, List.of(), node.tool(), node.params(), "workflow");
        return assistantService.assist(request).compose(response -> {
          String rawOutput = response.response();
          if (node.reflection()) {
            String reflectionPrompt = node.reflectionPrompt();
            if (reflectionPrompt == null || reflectionPrompt.isEmpty()) {
              reflectionPrompt = "Reflect on:\n" + rawOutput;
            } else {
              reflectionPrompt = reflectionPrompt.replace("{{prev}}", rawOutput);
            }
            AiRequest reflectionRequest = AiAssistantService.buildRequest(reflectionPrompt, List.of(), "assist",
              java.util.Map.of(), "workflow");
            return assistantService.assist(reflectionRequest).map(reflection -> {
              String output = reflection.response();
              boolean passed = node.expectedContains() == null || node.expectedContains().isEmpty()
                || output.contains(node.expectedContains());
              results.add(new AiWorkflowRunResult.NodeResult(node.id(), output, rawOutput, output, node.expectedContains(), passed));
              return output;
            });
          }
          boolean passed = node.expectedContains() == null || node.expectedContains().isEmpty()
            || rawOutput.contains(node.expectedContains());
          results.add(new AiWorkflowRunResult.NodeResult(node.id(), rawOutput, rawOutput, "", node.expectedContains(), passed));
          return Future.succeededFuture(rawOutput);
        });
      });
    }
    return chain.map(ignored -> new AiWorkflowRunResult(results))
      .compose(result -> appendRun(workflow.id(), result).map(result));
  }

  /**
   * Returns recent run history for a workflow.
   */
  public Future<JsonArray> listRuns(String workflowId, int limit) {
    Promise<JsonArray> promise = Promise.promise();
    if (workflowId == null || workflowId.isEmpty()) {
      promise.complete(new JsonArray());
      return promise.future();
    }
    Path runPath = workflowDir.resolve(workflowId + "-runs.jsonl");
    vertx.fileSystem().readFile(runPath.toString(), ar -> {
      if (ar.failed()) {
        promise.complete(new JsonArray());
        return;
      }
      String content = ar.result().toString(StandardCharsets.UTF_8);
      String[] lines = content.split("\\r?\\n");
      JsonArray runs = new JsonArray();
      int start = Math.max(0, lines.length - limit);
      for (int i = start; i < lines.length; i++) {
        String line = lines[i].trim();
        if (!line.isEmpty()) {
          runs.add(new JsonObject(line));
        }
      }
      promise.complete(runs);
    });
    return promise.future();
  }

  private Future<Void> appendRun(String workflowId, AiWorkflowRunResult result) {
    if (workflowId == null || workflowId.isEmpty()) {
      return Future.succeededFuture();
    }
    Path runPath = workflowDir.resolve(workflowId + "-runs.jsonl");
    return vertx.fileSystem().mkdirs(workflowDir.toString()).compose(ignored -> {
      OpenOptions options = new OpenOptions().setCreate(true).setWrite(true).setAppend(true);
      Buffer buffer = Buffer.buffer(result.toJson().encode() + "\n", StandardCharsets.UTF_8.name());
      Promise<Void> promise = Promise.promise();
      vertx.fileSystem().open(runPath.toString(), options, openAr -> {
        if (openAr.failed()) {
          promise.fail(openAr.cause());
          return;
        }
        openAr.result().write(buffer, writeAr -> {
          openAr.result().close();
          if (writeAr.failed()) {
            promise.fail(writeAr.cause());
          } else {
            promise.complete();
          }
        });
      });
      return promise.future();
    });
  }

  /**
   * Orders nodes using edges if possible, falling back to declaration order.
   */
  private static final class WorkflowOrderer {
    static List<AiWorkflowNode> order(List<AiWorkflowNode> nodes, JsonArray edges) {
      if (edges == null || edges.isEmpty()) {
        return nodes;
      }
      java.util.Map<String, AiWorkflowNode> map = new java.util.HashMap<>();
      java.util.Map<String, java.util.Set<String>> incoming = new java.util.HashMap<>();
      java.util.Map<String, java.util.Set<String>> outgoing = new java.util.HashMap<>();
      for (AiWorkflowNode node : nodes) {
        map.put(node.id(), node);
        incoming.put(node.id(), new java.util.HashSet<>());
        outgoing.put(node.id(), new java.util.HashSet<>());
      }
      for (int i = 0; i < edges.size(); i++) {
        JsonObject edge = edges.getJsonObject(i);
        String from = edge.getString("from", "");
        String to = edge.getString("to", "");
        if (!map.containsKey(from) || !map.containsKey(to)) {
          continue;
        }
        outgoing.get(from).add(to);
        incoming.get(to).add(from);
      }
      java.util.ArrayDeque<String> ready = new java.util.ArrayDeque<>();
      for (String id : incoming.keySet()) {
        if (incoming.get(id).isEmpty()) {
          ready.add(id);
        }
      }
      List<AiWorkflowNode> ordered = new ArrayList<>();
      while (!ready.isEmpty()) {
        String id = ready.removeFirst();
        ordered.add(map.get(id));
        for (String next : outgoing.get(id)) {
          java.util.Set<String> set = incoming.get(next);
          set.remove(id);
          if (set.isEmpty()) {
            ready.add(next);
          }
        }
      }
      if (ordered.size() != nodes.size()) {
        return nodes;
      }
      return ordered;
    }
  }
}
