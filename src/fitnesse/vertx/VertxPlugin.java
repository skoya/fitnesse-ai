package fitnesse.vertx;

/**
 * Registers Vert.x routes/eventbus handlers for a plugin.
 */
public interface VertxPlugin {
  void register(VertxPluginContext context);
}
