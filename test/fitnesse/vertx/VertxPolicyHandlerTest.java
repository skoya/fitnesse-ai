package fitnesse.vertx;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class VertxPolicyHandlerTest {

  @TempDir
  Path tempDir;

  @Test
  public void deniesAccessBasedOnFolderPolicy(Vertx vertx, VertxTestContext testContext) throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("root"));
    writePolicy(root, new JsonObject().put("default", new JsonObject().put("ui", "allow")));
    Path secure = Files.createDirectories(root.resolve("Secure"));
    writePolicy(secure, new JsonObject().put("default", new JsonObject().put("ui", "deny")));

    AccessPolicyResolver resolver = new AccessPolicyResolver(root, vertx.fileSystem());
    Router router = Router.router(vertx);
    router.route().handler(new VertxPolicyHandler(resolver, null));
    router.get("/wiki/*").handler(ctx -> ctx.response().setStatusCode(200).end("ok"));

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(0)
      .onComplete(testContext.succeeding(server -> {
        HttpClient client = vertx.createHttpClient();
        int port = server.actualPort();
        Future<Integer> secureStatus = requestStatus(client, port, "/wiki/Secure/Page");
        Future<Integer> openStatus = requestStatus(client, port, "/wiki/Public/Page");

        Future.all(secureStatus, openStatus).onComplete(testContext.succeeding(done -> {
          testContext.verify(() -> {
            assertEquals(403, secureStatus.result());
            assertEquals(200, openStatus.result());
          });
          client.close();
          server.close();
          testContext.completeNow();
        }));
      }));
  }

  private static Future<Integer> requestStatus(HttpClient client, int port, String path) {
    Promise<Integer> promise = Promise.promise();
    client.request(HttpMethod.GET, port, "localhost", path)
      .onFailure(promise::fail)
      .onSuccess(request -> request.send()
        .onFailure(promise::fail)
        .onSuccess(response -> promise.complete(response.statusCode())));
    return promise.future();
  }

  private static void writePolicy(Path root, JsonObject policy) throws Exception {
    Path dir = Files.createDirectories(root.resolve(".fitnesse"));
    Files.writeString(dir.resolve("policy.json"), policy.encodePrettily(), StandardCharsets.UTF_8);
  }
}
