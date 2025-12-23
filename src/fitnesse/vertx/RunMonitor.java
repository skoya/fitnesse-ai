package fitnesse.vertx;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks queued/running/completed test executions for backpressure and monitoring.
 */
public final class RunMonitor {
  private static final int MAX_LOGS = 2000;
  private static final int DEFAULT_LOG_LIMIT = 200;
  private final AtomicInteger queued = new AtomicInteger();
  private final AtomicInteger running = new AtomicInteger();
  private final AtomicLong completed = new AtomicLong();
  private final AtomicLong totalNanos = new AtomicLong();
  private final AtomicLong nextLogId = new AtomicLong();
  private final Deque<JsonObject> logs = new ArrayDeque<>();
  private volatile Handler<JsonObject> onUpdate;

  public RunMonitor() {
    this(null);
  }

  public RunMonitor(Handler<JsonObject> onUpdate) {
    this.onUpdate = onUpdate;
  }

  void setOnUpdate(Handler<JsonObject> onUpdate) {
    this.onUpdate = onUpdate;
  }

  boolean canAccept(int maxQueue) {
    if (maxQueue <= 0) {
      return true;
    }
    return queued.get() < maxQueue;
  }

  void incrementQueued(String resource) {
    queued.incrementAndGet();
    log("info", "Run queued", resource, null);
    publish();
  }

  /**
   * Marks a run as started and returns a nano timestamp for duration tracking.
   */
  long startRun(String resource) {
    queued.decrementAndGet();
    running.incrementAndGet();
    log("info", "Run started", resource, null);
    publish();
    return System.nanoTime();
  }

  void finishRun(long startNanos, String resource) {
    running.decrementAndGet();
    completed.incrementAndGet();
    if (startNanos > 0) {
      totalNanos.addAndGet(System.nanoTime() - startNanos);
      long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
      log("info", "Run finished in " + elapsedMillis + " ms", resource, null);
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

  JsonObject logsSince(long lastId, int limit) {
    if (limit <= 0) {
      limit = DEFAULT_LOG_LIMIT;
    }
    JsonArray entries = new JsonArray();
    long maxId = lastId;
    synchronized (logs) {
      for (JsonObject entry : logs) {
        long id = entry.getLong("id", 0L);
        if (id > lastId) {
          entries.add(entry.copy());
          maxId = Math.max(maxId, id);
          if (entries.size() >= limit) {
            break;
          }
        }
      }
    }
    return new JsonObject()
      .put("nextId", maxId)
      .put("entries", entries);
  }

  void log(String level, String message, String resource, String testSystem) {
    if (message == null || message.isBlank()) {
      return;
    }
    String trimmed = truncate(message, 2000);
    JsonObject entry = new JsonObject()
      .put("id", nextLogId.incrementAndGet())
      .put("timestamp", System.currentTimeMillis())
      .put("level", level)
      .put("message", trimmed)
      .put("resource", resource)
      .put("testSystem", testSystem);
    synchronized (logs) {
      logs.addLast(entry);
      while (logs.size() > MAX_LOGS) {
        logs.removeFirst();
      }
    }
  }

  private static String truncate(String value, int maxLength) {
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...(truncated)";
  }

  private void publish() {
    if (onUpdate != null) {
      onUpdate.handle(snapshot());
    }
  }
}
