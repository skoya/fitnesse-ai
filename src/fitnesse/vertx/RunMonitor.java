package fitnesse.vertx;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks queued/running/completed test executions for backpressure and monitoring.
 */
final class RunMonitor {
  private final AtomicInteger queued = new AtomicInteger();
  private final AtomicInteger running = new AtomicInteger();
  private final AtomicLong completed = new AtomicLong();
  private final AtomicLong totalNanos = new AtomicLong();
  private final Handler<JsonObject> onUpdate;

  RunMonitor() {
    this(null);
  }

  RunMonitor(Handler<JsonObject> onUpdate) {
    this.onUpdate = onUpdate;
  }

  boolean canAccept(int maxQueue) {
    if (maxQueue <= 0) {
      return true;
    }
    return queued.get() < maxQueue;
  }

  void incrementQueued() {
    queued.incrementAndGet();
    publish();
  }

  /**
   * Marks a run as started and returns a nano timestamp for duration tracking.
   */
  long startRun() {
    queued.decrementAndGet();
    running.incrementAndGet();
    publish();
    return System.nanoTime();
  }

  void finishRun(long startNanos) {
    running.decrementAndGet();
    completed.incrementAndGet();
    if (startNanos > 0) {
      totalNanos.addAndGet(System.nanoTime() - startNanos);
    }
    publish();
  }

  /**
   * Captures current queue/running/completed counts and rolling average duration.
   */
  JsonObject snapshot() {
    long done = completed.get();
    long avgMillis = done == 0 ? 0 : totalNanos.get() / done / 1_000_000;
    return new JsonObject()
      .put("queued", queued.get())
      .put("running", running.get())
      .put("completed", done)
      .put("averageMillis", avgMillis);
  }

  private void publish() {
    if (onUpdate != null) {
      onUpdate.handle(snapshot());
    }
  }
}
