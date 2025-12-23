package fitnesse.mcp;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.http.HttpServerRequest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes MCP audit events to a JSONL file under the wiki root.
 */
public final class McpAuditLogger {
  private final Vertx vertx;
  private final Path logPath;

  public McpAuditLogger(Vertx vertx, String rootPath, String rootDirectory) {
    this.vertx = vertx;
    this.logPath = Paths.get(rootPath, rootDirectory, ".fitnesse", "audit", "mcp-audit.jsonl");
  }

  public void log(String actor, String action, String resource, HttpServerRequest request,
                  int status, JsonObject payload) {
    JsonObject event = new JsonObject()
      .put("eventId", UUID.randomUUID().toString())
      .put("timestamp", Instant.now().toString())
      .put("actor", actor)
      .put("action", action)
      .put("resource", resource)
      .put("correlationId", request.getHeader("X-Request-Id"))
      .put("version", 1)
      .put("payload", payload == null ? new JsonObject() : payload)
      .put("method", request.method().name())
      .put("path", request.path())
      .put("status", status);

    writeEvent(event);
  }

  public void logGrpc(String actor, String action, String resource, int status, JsonObject payload) {
    JsonObject event = new JsonObject()
      .put("eventId", UUID.randomUUID().toString())
      .put("timestamp", Instant.now().toString())
      .put("actor", actor)
      .put("action", action)
      .put("resource", resource)
      .put("correlationId", "")
      .put("version", 1)
      .put("payload", payload == null ? new JsonObject() : payload)
      .put("method", "grpc")
      .put("path", resource)
      .put("status", status);

    writeEvent(event);
  }

  public void logWebSocket(String actor, String action, String resource, int status, JsonObject payload) {
    JsonObject event = new JsonObject()
      .put("eventId", UUID.randomUUID().toString())
      .put("timestamp", Instant.now().toString())
      .put("actor", actor)
      .put("action", action)
      .put("resource", resource)
      .put("correlationId", "")
      .put("version", 1)
      .put("payload", payload == null ? new JsonObject() : payload)
      .put("method", "ws")
      .put("path", resource)
      .put("status", status);

    writeEvent(event);
  }

  private void writeEvent(JsonObject event) {
    String directory = logPath.getParent().toString();
    vertx.fileSystem().mkdirs(directory, mkdirRes -> {
      if (mkdirRes.failed()) {
        return;
      }
      OpenOptions options = new OpenOptions().setWrite(true).setCreate(true).setAppend(true);
      vertx.fileSystem().open(logPath.toString(), options, openRes -> {
        if (openRes.failed()) {
          return;
        }
        Buffer buffer = Buffer.buffer(event.encode() + System.lineSeparator());
        openRes.result().write(buffer, writeRes -> openRes.result().close());
      });
    });
  }
}
