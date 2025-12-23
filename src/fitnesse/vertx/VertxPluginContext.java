package fitnesse.vertx;

import fitnesse.FitNesseContext;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;

/**
 * Provides shared Vert.x components to plugins.
 */
public final class VertxPluginContext {
  public final Vertx vertx;
  public final Router router;
  public final EventBus bus;
  public final FitNesseContext fitnesseContext;
  public final VertxConfig config;

  public VertxPluginContext(Vertx vertx, Router router, EventBus bus, FitNesseContext fitnesseContext, VertxConfig config) {
    this.vertx = vertx;
    this.router = router;
    this.bus = bus;
    this.fitnesseContext = fitnesseContext;
    this.config = config;
  }
}
