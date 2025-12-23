package fitnesse.vertx;

import fitnesse.FitNesseContext;
import fitnesse.responders.WikiPageResponder;
import fitnesse.testutil.FitNesseUtil;
import fitnesse.wiki.PathParser;
import fitnesse.wiki.WikiPageUtil;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class VertxPerformanceSmokeTest {
  @Test
  void handlesConcurrentPageViews(Vertx vertx, VertxTestContext ctx) throws Exception {
    FitNesseContext context = FitNesseUtil.makeTestContext();
    WikiPageUtil.addPage(context.getRootPage(), PathParser.parse("FrontPage"), "hello");

    ResponderBusService busService = new ResponderBusService(vertx, context);
    EventBus bus = vertx.eventBus();
    busService.register(bus, "fitnesse.page.view", new WikiPageResponder());

    Router router = Router.router(vertx);
    router.get("/wiki/*").handler(rc -> {
      String resource = rc.request().path().substring("/wiki/".length());
      bus.request("fitnesse.page.view", busService.buildPayload(rc, resource))
        .onComplete(ar -> {
          if (ar.succeeded()) {
            busService.writeResponse(rc, (io.vertx.core.json.JsonObject) ar.result().body());
          } else {
            rc.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(0)
      .onSuccess(server -> {
        int port = server.actualPort();
        WebClient client = WebClient.create(vertx);
        int requestCount = 20;
        AtomicInteger remaining = new AtomicInteger(requestCount);
        long start = System.nanoTime();

        for (int i = 0; i < requestCount; i++) {
          client.get(port, "localhost", "/wiki/FrontPage").send().onComplete(ar -> {
            ctx.verify(() -> {
              assertTrue(ar.succeeded());
              assertEquals(200, ar.result().statusCode());
            });
            if (remaining.decrementAndGet() == 0) {
              long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
              ctx.verify(() -> assertTrue(elapsedMillis < 10_000L));
              server.close();
              try {
                FitNesseUtil.destroyTestContext(context);
              } catch (Exception ignored) {
                // Ignore cleanup failures in tests.
              }
              ctx.completeNow();
            }
          });
        }
      })
      .onFailure(ctx::failNow);
  }
}
