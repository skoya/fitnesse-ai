package fitnesse.vertx;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.TimeUnit;

/**
 * Loads Vert.x configuration (file + env) and merges with defaults.
 */
public final class VertxConfigLoader {

  private VertxConfigLoader() {
  }

  public static VertxConfig load(Vertx vertx) {
    return load(vertx, VertxConfig.fromEnv());
  }

  public static VertxConfig load(Vertx vertx, VertxConfig fallback) {
    ConfigStoreOptions fileStore = new ConfigStoreOptions()
      .setType("file")
      .setOptional(true)
      .setConfig(new JsonObject().put("path", "vertx-config.json"));

    ConfigStoreOptions envStore = new ConfigStoreOptions().setType("env");

    ConfigRetrieverOptions options = new ConfigRetrieverOptions()
      .addStore(fileStore)
      .addStore(envStore);

    JsonObject loaded = new JsonObject();
    try {
      JsonObject cfg = ConfigRetriever.create(vertx, options)
        .getConfig()
        .toCompletionStage()
        .toCompletableFuture()
        .get(2, TimeUnit.SECONDS);
      if (cfg != null) {
        loaded = cfg;
      }
    } catch (Exception ignored) {
      // Fall back to provided defaults.
    }
    return VertxConfig.fromJson(loaded, fallback);
  }
}
