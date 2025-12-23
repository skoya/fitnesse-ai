package fitnesse.vertx;

import fitnesse.FitNesseContext;
import fitnesse.Responder;
import fitnesse.http.Request;
import fitnesse.http.Response;
import fitnesse.http.SimpleResponse;
import fitnesse.testutil.FitNesseUtil;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(VertxExtension.class)
class VertxBackpressurePerformanceTest {
  @Test
  void rejectsRequestsWhenQueueIsFull(Vertx vertx, VertxTestContext ctx) throws Exception {
    FitNesseContext context = FitNesseUtil.makeTestContext();
    ResponderBusService busService = new ResponderBusService(vertx, context);
    RunMonitor monitor = new RunMonitor();
    WorkerExecutor executor = vertx.createSharedWorkerExecutor("fitnesse-backpressure-test", 1);

    String address = "fitnesse.test.backpressure";
    busService.registerWithExecutor(vertx.eventBus(), address, new SlowResponder(200), executor, monitor, 2);

    JsonObject payload = new JsonObject()
      .put(ResponderBusService.HEADER_RESOURCE, "FrontPage")
      .put(ResponderBusService.HEADER_CONTEXT_ROOT, context.contextRoot)
      .put(ResponderBusService.HEADER_HEADERS, new JsonObject())
      .put(ResponderBusService.HEADER_PARAMS, new JsonObject())
      .put(ResponderBusService.HEADER_UPLOADS, new JsonArray());

    int requestCount = 10;
    AtomicInteger remaining = new AtomicInteger(requestCount);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    EventBus bus = vertx.eventBus();

    for (int i = 0; i < requestCount; i++) {
      bus.request(address, payload).onComplete(ar -> {
        ctx.verify(() -> {
          if (ar.succeeded()) {
            successes.incrementAndGet();
          } else if (ar.cause() instanceof ReplyException) {
            ReplyException reply = (ReplyException) ar.cause();
            if (reply.failureCode() == 429) {
              rejected.incrementAndGet();
            } else {
              fail("Unexpected failure code: " + reply.failureCode());
            }
          } else {
            fail("Unexpected error: " + ar.cause());
          }
        });
        if (remaining.decrementAndGet() == 0) {
          ctx.verify(() -> {
            assertTrue(successes.get() > 0);
            assertTrue(rejected.get() > 0);
          });
          executor.close();
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

  private static final class SlowResponder implements Responder {
    private final long delayMillis;

    private SlowResponder(long delayMillis) {
      this.delayMillis = delayMillis;
    }

    @Override
    public Response makeResponse(FitNesseContext context, Request request) throws Exception {
      Thread.sleep(delayMillis);
      SimpleResponse response = new SimpleResponse();
      response.setContent("ok");
      return response;
    }
  }
}
