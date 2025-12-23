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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class VertxConcurrentLoadTest {
  @Test
  void handlesConcurrentTrafficBursts(Vertx vertx, VertxTestContext ctx) throws Exception {
    FitNesseContext context = FitNesseUtil.makeTestContext();
    WikiPageUtil.addPage(context.getRootPage(), PathParser.parse("FrontPage"), "hello");

    ResponderBusService busService = new ResponderBusService(vertx, context);
    EventBus bus = vertx.eventBus();
    busService.register(bus, "fitnesse.page.view", new WikiPageResponder());

    Router router = Router.router(vertx);
    router.get("/wiki/*").handler(rc -> {
      String resource = rc.request().path().substring("/wiki/".length());
      bus.request("fitnesse.page.view", busService.buildPayload(rc, resource), ar -> {
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
        int users = 25;
        int requestsPerUser = 4;
        int totalRequests = users * requestsPerUser;
        AtomicInteger remaining = new AtomicInteger(totalRequests);
        AtomicLong totalLatencyMillis = new AtomicLong();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>(totalRequests));
        long startSuite = System.nanoTime();

        for (int user = 0; user < users; user++) {
          for (int i = 0; i < requestsPerUser; i++) {
            long start = System.nanoTime();
            client.get(port, "localhost", "/wiki/FrontPage").send(ar -> {
              long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
              latencies.add(elapsedMillis);
              totalLatencyMillis.addAndGet(elapsedMillis);
              ctx.verify(() -> {
                assertTrue(ar.succeeded());
                assertEquals(200, ar.result().statusCode());
              });
              if (remaining.decrementAndGet() == 0) {
                long totalElapsedMillis = (System.nanoTime() - startSuite) / 1_000_000L;
                List<Long> sorted = new ArrayList<>(latencies);
                Collections.sort(sorted);
                long p50 = percentile(sorted, 0.50);
                long p95 = percentile(sorted, 0.95);
                long avg = totalLatencyMillis.get() / Math.max(1, latencies.size());
                double rps = totalElapsedMillis == 0 ? totalRequests : (totalRequests * 1000.0) / totalElapsedMillis;

                System.out.printf(
                  "Load test: users=%d requestsPerUser=%d total=%d elapsedMs=%d avgMs=%d p50Ms=%d p95Ms=%d rps=%.2f%n",
                  users, requestsPerUser, totalRequests, totalElapsedMillis, avg, p50, p95, rps);

                ctx.verify(() -> assertTrue(totalElapsedMillis < 60_000L));
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
        }
      })
      .onFailure(ctx::failNow);
  }

  private static long percentile(List<Long> sorted, double percentile) {
    if (sorted.isEmpty()) {
      return 0L;
    }
    int index = (int) Math.ceil(percentile * sorted.size()) - 1;
    index = Math.max(0, Math.min(sorted.size() - 1, index));
    return sorted.get(index);
  }
}
