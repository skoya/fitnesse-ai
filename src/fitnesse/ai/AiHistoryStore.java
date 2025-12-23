package fitnesse.ai;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Append-only history store for AI requests and responses.
 */
public final class AiHistoryStore {
  private final Vertx vertx;
  private final Path historyFile;

  public AiHistoryStore(Vertx vertx, Path rootDir) {
    this.vertx = vertx;
    this.historyFile = rootDir.resolve(".fitnesse").resolve("ai").resolve("history.jsonl");
  }

  /**
   * Appends a request/response pair as a JSON line.
   */
  public Future<Void> append(AiRequest request, AiResponse response) {
    Path parent = historyFile.getParent();
    OpenOptions options = new OpenOptions().setCreate(true).setWrite(true).setAppend(true);
    JsonObject entry = new JsonObject()
      .put("prompt", request.prompt())
      .put("grounding", request.grounding())
      .put("tool", request.tool())
      .put("parameters", request.parameters())
      .put("conversationId", request.conversationId())
      .put("requestedAt", request.timestamp().toString())
      .put("response", response.response())
      .put("respondedAt", response.timestamp().toString());
    Buffer buffer = Buffer.buffer(entry.encode() + "\n", StandardCharsets.UTF_8.name());
    return vertx.fileSystem().mkdirs(parent.toString())
      .compose(ignored -> vertx.fileSystem().open(historyFile.toString(), options))
      .compose(file -> file.write(buffer, file.getWritePos()).compose(ignored -> file.close()));
  }
}
