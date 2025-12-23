package fitnesse.vertx;

import fitnesse.docstore.GitHistoryService;
import fitnesse.docstore.HistoryQuery;
import fitnesse.docstore.PageHistory;
import fitnesse.docstore.PageHistoryEntry;
import fitnesse.docstore.PageRef;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.file.Path;
import java.util.Iterator;

final class GitBusService {
  static final String ADDRESS_HISTORY = "fitnesse.git.history";
  static final String ADDRESS_DIFF = "fitnesse.git.diff";
  static final String ADDRESS_REVERT = "fitnesse.git.revert";
  private static final String CACHE_NAME = "fitnesse.git.cache";
  private static final long CACHE_TTL_MS = 3000L;

  private final Vertx vertx;
  private final GitHistoryService historyService;
  private final LocalMap<String, JsonObject> cache;

  GitBusService(Vertx vertx, Path repoRoot) {
    this.vertx = vertx;
    this.historyService = new GitHistoryService(repoRoot);
    this.cache = vertx.sharedData().getLocalMap(CACHE_NAME);
  }

  void register(EventBus bus) {
    bus.consumer(ADDRESS_HISTORY, message -> {
      JsonObject payload = (JsonObject) message.body();
      String path = payload.getString("path", "FrontPage");
      int limit = payload.getInteger("limit", 50);
      String cacheKey = "history:" + path + ":" + limit;
      JsonObject cached = cache.get(cacheKey);
      if (isFresh(cached)) {
        message.reply(cached.getJsonObject("payload"));
        return;
      }
      vertx.executeBlocking(() -> {
        PageHistory history = historyService.history(new PageRef(path), new HistoryQuery(limit));
        JsonArray entries = new JsonArray();
        for (PageHistoryEntry entry : history.entries()) {
          JsonObject json = new JsonObject();
          json.put("commitId", entry.commitId());
          json.put("author", entry.author());
          json.put("authorEmail", entry.authorEmail());
          json.put("message", entry.message());
          json.put("timestamp", entry.timestamp().toString());
          entries.add(json);
        }
        return new JsonObject().put("entries", entries);
      }, false).onComplete(ar -> {
        if (ar.succeeded()) {
          JsonObject payloadJson = (JsonObject) ar.result();
          cache.put(cacheKey, cacheEntry(payloadJson));
          message.reply(payloadJson);
        } else {
          message.fail(500, ar.cause().getMessage());
        }
      });
    });

    bus.consumer(ADDRESS_DIFF, message -> {
      JsonObject payload = (JsonObject) message.body();
      String path = payload.getString("path", "FrontPage");
      String commitId = payload.getString("commitId");
      if (commitId == null || commitId.isEmpty()) {
        message.fail(400, "commitId is required");
        return;
      }
      String cacheKey = "diff:" + path + ":" + commitId;
      JsonObject cached = cache.get(cacheKey);
      if (isFresh(cached)) {
        message.reply(cached.getJsonObject("payload"));
        return;
      }
      vertx.executeBlocking(() -> {
        String diff = historyService.diff(new PageRef(path), commitId);
        return new JsonObject().put("diff", diff);
      }, false).onComplete(ar -> {
        if (ar.succeeded()) {
          JsonObject payloadJson = (JsonObject) ar.result();
          cache.put(cacheKey, cacheEntry(payloadJson));
          message.reply(payloadJson);
        } else {
          message.fail(500, ar.cause().getMessage());
        }
      });
    });

    bus.consumer(ADDRESS_REVERT, message -> {
      JsonObject payload = (JsonObject) message.body();
      String path = payload.getString("path", "FrontPage");
      String commitId = payload.getString("commitId");
      if (commitId == null || commitId.isEmpty()) {
        message.fail(400, "commitId is required");
        return;
      }
      String lockKey = "fitnesse.git.revert:" + path;
      vertx.sharedData().getLocalLock(lockKey).onComplete(lockResult -> {
        if (lockResult.failed()) {
          message.fail(500, lockResult.cause().getMessage());
          return;
        }
        Lock lock = lockResult.result();
        vertx.executeBlocking(() -> {
          historyService.revert(new PageRef(path), commitId);
          return null;
        }, false).onComplete(ar -> {
          lock.release();
          if (ar.succeeded()) {
            clearCacheForPath(path);
            message.reply(new JsonObject().put("status", "ok"));
          } else {
            message.fail(500, ar.cause().getMessage());
          }
        });
      });
    });
  }

  private boolean isFresh(JsonObject cached) {
    if (cached == null) {
      return false;
    }
    long timestamp = cached.getLong("timestamp", 0L);
    return (System.currentTimeMillis() - timestamp) <= CACHE_TTL_MS;
  }

  private JsonObject cacheEntry(JsonObject payload) {
    return new JsonObject()
      .put("timestamp", System.currentTimeMillis())
      .put("payload", payload);
  }

  private void clearCacheForPath(String path) {
    Iterator<String> iterator = cache.keySet().iterator();
    while (iterator.hasNext()) {
      String key = iterator.next();
      if (key.startsWith("history:" + path + ":") || key.startsWith("diff:" + path + ":")) {
        iterator.remove();
      }
    }
  }
}
