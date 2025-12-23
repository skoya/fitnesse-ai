package fitnesse.vertx;

import fitnesse.search.SearchResult;
import fitnesse.search.SearchService;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;

import java.util.ArrayList;
import java.util.List;

/**
 * EventBus facade for SearchService with small shared-data cache.
 */
final class SearchBusService {
  static final String ADDRESS_SEARCH = "fitnesse.search";
  private static final String CACHE_NAME = "fitnesse.search.cache";
  private static final long CACHE_TTL_MS = 2000L;

  private final Vertx vertx;
  private final SearchService searchService;
  private final LocalMap<String, JsonObject> cache;

  SearchBusService(Vertx vertx, SearchService searchService) {
    this.vertx = vertx;
    this.searchService = searchService;
    this.cache = vertx.sharedData().getLocalMap(CACHE_NAME);
  }

  /**
   * Registers the search consumer on the EventBus.
   */
  void register(EventBus bus) {
    bus.consumer(ADDRESS_SEARCH, message -> {
      JsonObject payload = (JsonObject) message.body();
      String query = payload.getString("query", "");
      String type = payload.getString("type", "content");
      String tags = payload.getString("tags", "");
      String pageType = payload.getString("pageType", "any");
      int limit = payload.getInteger("limit", 50);
      int offset = payload.getInteger("offset", 0);

      String cacheKey = String.join("|", query, type, tags, pageType, String.valueOf(limit), String.valueOf(offset));
      JsonObject cached = cache.get(cacheKey);
      if (isFresh(cached)) {
        message.reply(cached.getJsonObject("payload"));
        return;
      }

      SearchService.Mode mode = "title".equalsIgnoreCase(type) ? SearchService.Mode.TITLE : SearchService.Mode.CONTENT;
      SearchService.PageTypeFilter pageTypeFilter = parsePageTypeFilter(pageType);
      List<String> tagFilters = parseTags(tags);

      vertx.executeBlocking(() -> {
        List<SearchResult> results = searchService.search(query, mode, limit, offset, tagFilters, pageTypeFilter);
        return buildPayload(query, mode, tags, pageType, limit, offset, results);
      }, false).onComplete(ar -> {
        if (ar.succeeded()) {
          JsonObject response = (JsonObject) ar.result();
          cache.put(cacheKey, cacheEntry(response));
          message.reply(response);
        } else {
          message.fail(500, ar.cause().getMessage());
        }
      });
    });
  }

  /**
   * Converts a JSON array to SearchResult objects.
   */
  static List<SearchResult> toResults(JsonArray array) {
    List<SearchResult> results = new ArrayList<>();
    if (array == null) {
      return results;
    }
    for (int i = 0; i < array.size(); i++) {
      JsonObject entry = array.getJsonObject(i);
      if (entry == null) {
        continue;
      }
      results.add(new SearchResult(entry.getString("path", ""), entry.getString("snippet", "")));
    }
    return results;
  }

  private JsonObject buildPayload(String query, SearchService.Mode mode, String tags, String pageType,
                                  int limit, int offset, List<SearchResult> results) {
    JsonArray array = new JsonArray();
    JsonArray grounding = new JsonArray();
    for (SearchResult result : results) {
      JsonObject entry = new JsonObject()
        .put("path", result.path())
        .put("snippet", result.snippet());
      array.add(entry);
      grounding.add(new JsonObject()
        .put("id", result.path())
        .put("text", result.snippet()));
    }
    return new JsonObject()
      .put("query", query == null ? "" : query)
      .put("type", mode.name().toLowerCase())
      .put("tags", tags == null ? "" : tags)
      .put("pageType", pageType == null ? "" : pageType)
      .put("offset", offset)
      .put("limit", limit)
      .put("nextOffset", results.size() == limit ? offset + limit : -1)
      .put("count", results.size())
      .put("results", array)
      .put("grounding", new JsonObject().put("documents", grounding));
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

  private static List<String> parseTags(String tags) {
    List<String> values = new ArrayList<>();
    if (tags == null || tags.isEmpty()) {
      return values;
    }
    for (String tag : tags.split(",")) {
      String normalized = tag.trim();
      if (!normalized.isEmpty()) {
        values.add(normalized.toLowerCase());
      }
    }
    return values;
  }

  private static SearchService.PageTypeFilter parsePageTypeFilter(String pageType) {
    if (pageType == null || pageType.isEmpty() || "any".equalsIgnoreCase(pageType)) {
      return SearchService.PageTypeFilter.ANY;
    }
    if ("suite".equalsIgnoreCase(pageType)) {
      return SearchService.PageTypeFilter.SUITE;
    }
    if ("test".equalsIgnoreCase(pageType)) {
      return SearchService.PageTypeFilter.TEST;
    }
    return SearchService.PageTypeFilter.ANY;
  }
}
