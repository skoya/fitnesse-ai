package fitnesse.vertx;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects Vert.x plugins loaded via FitNesse plugin system.
 */
public final class VertxPluginRegistry {
  private final List<VertxPlugin> plugins = new ArrayList<>();

  public void add(VertxPlugin plugin) {
    if (plugin != null) {
      plugins.add(plugin);
    }
  }

  public void registerAll(VertxPluginContext context) {
    for (VertxPlugin plugin : plugins) {
      plugin.register(context);
    }
  }
}
