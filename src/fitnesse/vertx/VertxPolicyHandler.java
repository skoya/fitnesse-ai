package fitnesse.vertx;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Enforces AccessPolicy per surface (UI/API/MCP) and delegates to auth when required.
 */
final class VertxPolicyHandler implements Handler<RoutingContext> {
  private final AccessPolicyResolver policy;
  private final Handler<RoutingContext> authHandler;

  VertxPolicyHandler(AccessPolicyResolver policy, Handler<RoutingContext> authHandler) {
    this.policy = policy;
    this.authHandler = authHandler;
  }

  @Override
  public void handle(RoutingContext ctx) {
    AccessPolicy.Surface surface = surface(ctx);
    AccessPolicy.Decision decision = policy.decide(resolvePolicyPath(ctx), surface);
    switch (decision) {
      case DENY -> ctx.response().setStatusCode(403).end("Forbidden");
      case AUTH_REQUIRED -> {
        if (authHandler == null) {
          ctx.response().setStatusCode(401).end("Unauthorized");
        } else {
          authHandler.handle(ctx);
        }
      }
      default -> ctx.next();
    }
  }

  private AccessPolicy.Surface surface(RoutingContext ctx) {
    String path = ctx.normalisedPath();
    if (path.startsWith("/api/")) {
      return AccessPolicy.Surface.API;
    }
    if (path.startsWith("/mcp")) {
      return AccessPolicy.Surface.MCP;
    }
    return AccessPolicy.Surface.UI;
  }

  private String resolvePolicyPath(RoutingContext ctx) {
    String path = ctx.normalisedPath();
    if (path.startsWith("/wiki/")) {
      return stripLeadingSlash(path.substring("/wiki/".length()));
    }
    if (path.startsWith("/history/")) {
      return stripLeadingSlash(path.substring("/history/".length()));
    }
    if (path.startsWith("/diff/")) {
      return stripLeadingSlash(path.substring("/diff/".length()));
    }
    if (path.startsWith("/revert/")) {
      return stripLeadingSlash(path.substring("/revert/".length()));
    }
    if (path.startsWith("/api/history/")) {
      return stripLeadingSlash(path.substring("/api/history/".length()));
    }
    if (path.startsWith("/api/diff/")) {
      return stripLeadingSlash(path.substring("/api/diff/".length()));
    }
    if (path.startsWith("/api/revert/")) {
      return stripLeadingSlash(path.substring("/api/revert/".length()));
    }
    if (path.equals("/run")) {
      String suite = ctx.request().getParam("suite");
      if (suite != null && !suite.isBlank()) {
        return stripLeadingSlash(suite);
      }
      String test = ctx.request().getParam("test");
      if (test != null && !test.isBlank()) {
        return stripLeadingSlash(test);
      }
    }
    return "";
  }

  private String stripLeadingSlash(String value) {
    if (value == null) {
      return "";
    }
    return value.startsWith("/") ? value.substring(1) : value;
  }
}
