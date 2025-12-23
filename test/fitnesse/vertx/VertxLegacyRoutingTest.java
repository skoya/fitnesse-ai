package fitnesse.vertx;

import fitnesse.FitNesseContext;
import fitnesse.responders.files.FileResponder;
import fitnesse.testutil.FitNesseUtil;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class VertxLegacyRoutingTest {
  @Test
  void servesFilesAndRedirectsLegacyPages(Vertx vertx, VertxTestContext ctx) throws Exception {
    FitNesseContext context = FitNesseUtil.makeTestContext();
    Path root = Path.of(context.getRootPagePath());
    Path filesDir = root.resolve("files");
    Files.createDirectories(filesDir);
    Files.writeString(filesDir.resolve("sample.txt"), "hello", StandardCharsets.UTF_8);

    ResponderBusService busService = new ResponderBusService(vertx, context);
    EventBus bus = vertx.eventBus();
    busService.register(bus, "fitnesse.files", new FileResponder());

    Router router = Router.router(vertx);
    router.get("/files").handler(rc -> handleFiles(rc, bus, busService));
    router.get("/files/").handler(rc -> handleFiles(rc, bus, busService));
    router.get("/files/*").handler(rc -> handleFiles(rc, bus, busService));
    router.get("/:legacyPage").handler(rc -> {
      String legacyPage = rc.pathParam("legacyPage");
      if (isReservedPath(legacyPage)) {
        rc.next();
        return;
      }
      rc.response().setStatusCode(302).putHeader("Location", "/wiki/" + legacyPage).end();
    });

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(0)
      .onSuccess(server -> {
        int port = server.actualPort();
        WebClient client = WebClient.create(vertx, new WebClientOptions().setFollowRedirects(false));

        AtomicInteger remaining = new AtomicInteger(3);

        client.get(port, "localhost", "/files/").send().onComplete(ar -> {
          ctx.verify(() -> {
            assertTrue(ar.succeeded());
            var response = ar.result();
            String body = response.bodyAsString();
            assertEquals(200, response.statusCode());
            assertTrue(body.contains("sample.txt"), () -> "Directory listing body was: " + body);
          });
          completeIfDone(ctx, remaining, server, context);
        });

        client.get(port, "localhost", "/files/sample.txt").send().onComplete(ar -> {
          ctx.verify(() -> {
            assertTrue(ar.succeeded());
            var response = ar.result();
            assertEquals(200, response.statusCode());
            assertEquals("hello", response.bodyAsString());
          });
          completeIfDone(ctx, remaining, server, context);
        });

        client.get(port, "localhost", "/RecentChanges").send().onComplete(ar -> {
          ctx.verify(() -> {
            assertTrue(ar.succeeded());
            var response = ar.result();
            assertEquals(302, response.statusCode());
            assertNotNull(response.getHeader("Location"));
            assertEquals("/wiki/RecentChanges", response.getHeader("Location"));
          });
          completeIfDone(ctx, remaining, server, context);
        });
      })
      .onFailure(ctx::failNow);
  }

  private static void handleFiles(RoutingContext ctx, EventBus bus, ResponderBusService busService) {
    String resource = ctx.request().path().startsWith("/files")
      ? ctx.request().path().substring(1)
      : ctx.request().path();
    bus.request("fitnesse.files", busService.buildPayload(ctx, resource))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          busService.writeResponse(ctx, (io.vertx.core.json.JsonObject) ar.result().body());
        } else {
          ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
        }
      });
  }

  private static boolean isReservedPath(String path) {
    if (path == null || path.isEmpty()) {
      return true;
    }
    return "files".equals(path)
      || "search".equals(path)
      || "history".equals(path)
      || "diff".equals(path)
      || "results".equals(path)
      || "api".equals(path)
      || "run".equals(path)
      || "run-monitor".equals(path)
      || "ai".equals(path)
      || "agent".equals(path)
      || "eventbus".equals(path)
      || "plantuml".equals(path)
      || "metrics".equals(path)
      || "favicon.ico".equals(path);
  }

  private static void completeIfDone(VertxTestContext ctx,
                                     AtomicInteger remaining,
                                     io.vertx.core.http.HttpServer server,
                                     FitNesseContext context) {
    if (remaining.decrementAndGet() == 0) {
      server.close();
      try {
        FitNesseUtil.destroyTestContext(context);
      } catch (Exception ignored) {
        // Ignore cleanup failures in tests.
      }
      ctx.completeNow();
    }
  }
}
