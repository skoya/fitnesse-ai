package fitnesse.vertx;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
class VertxJUnit5SmokeTest {

  @Test
  void eventBusRequestReplyWorks(Vertx vertx, VertxTestContext ctx) {
    vertx.eventBus().consumer("test.echo", message -> message.reply(message.body()));

    vertx.eventBus()
      .<String>request("test.echo", "ping")
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        assertEquals("ping", reply.body());
        ctx.completeNow();
      })));
  }
}
