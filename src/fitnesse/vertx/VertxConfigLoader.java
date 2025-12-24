package fitnesse.vertx;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    writeDefaultsIfMissing(vertx, fallback);
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
      JsonObject cfg = VertxFutures.await(ConfigRetriever.create(vertx, options).getConfig(), 2, TimeUnit.SECONDS);
      if (cfg != null) {
        loaded = cfg;
      }
    } catch (Exception ignored) {
      // Fall back to provided defaults.
    }
    return VertxConfig.fromJson(loaded, fallback);
  }

  private static void writeDefaultsIfMissing(Vertx vertx, VertxConfig fallback) {
    Path path = Paths.get("vertx-config.json");
    FileSystem fs = vertx.fileSystem();
    if (fs.existsBlocking(path.toString())) {
      return;
    }
    try {
      Buffer buffer = Buffer.buffer(fallback.toJson().encodePrettily(), StandardCharsets.UTF_8.name());
      fs.writeFileBlocking(path.toString(), buffer);
    } catch (Exception ignored) {
      // If we can't write the file, just continue with defaults.
    }
  }
}
