package fitnesse.vertx;

import io.vertx.core.Future;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public final class VertxFutures {
  private VertxFutures() {
  }

  public static <T> T await(Future<T> future, long timeout, TimeUnit unit) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    future.onComplete(ar -> {
      if (ar.succeeded()) {
        result.set(ar.result());
      } else {
        error.set(ar.cause());
      }
      latch.countDown();
    });

    if (!latch.await(timeout, unit)) {
      throw new TimeoutException("Timed out waiting for Vert.x future");
    }
    if (error.get() != null) {
      Throwable cause = error.get();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      throw new RuntimeException(cause);
    }
    return result.get();
  }
}
