package fitnesse.mcp;

import fitnesse.plugins.PluginException;
import fitnesse.plugins.PluginFeatureFactoryBase;
import fitnesse.search.SearchResult;
import fitnesse.vertx.VertxPlugin;
import fitnesse.vertx.VertxPluginContext;
import fitnesse.vertx.VertxPluginRegistry;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Registers MCP adapters as FitNesse plugins.
 */
public class McpPlugin extends PluginFeatureFactoryBase {
  @Override
  public void registerVertxPlugins(VertxPluginRegistry registry) throws PluginException {
    registry.add(new McpHttpPlugin());
  }

  private static final class McpHttpPlugin implements VertxPlugin {
    @Override
    public void register(VertxPluginContext context) {
      McpService service = new McpService(context.fitnesseContext.getRootPage());
      McpAccessController access = new McpAccessController(context.fitnesseContext.authenticator, context.config.authEnabled());
      McpAuditLogger audit = new McpAuditLogger(context.vertx, context.config.rootPath(), context.config.rootDirectory());

      context.router.get("/mcp/health").handler(ctx -> {
        if (!access.authorize(ctx)) {
          return;
        }
        ctx.response().putHeader("Content-Type", "application/json");
        ctx.response().end(new JsonObject().put("status", "ok").encode());
        audit.log(access.resolveActor(ctx), "mcp.health", "/mcp/health", ctx.request(), 200, new JsonObject());
      });

      context.router.get("/mcp/resources").handler(ctx -> {
        if (!access.authorize(ctx)) {
          return;
        }
        int limit = parseInt(ctx.request().getParam("limit"), 50);
        int offset = parseInt(ctx.request().getParam("offset"), 0);
        McpService.PageListing listing = service.listPages(limit, offset);
        JsonArray resources = new JsonArray();
        for (McpService.PageSummary summary : listing.pages()) {
          resources.add(new JsonObject()
            .put("id", summary.path())
            .put("name", summary.name())
            .put("uri", "/mcp/page/" + summary.path())
            .put("type", "wiki-page"));
        }
        ctx.response().putHeader("Content-Type", "application/json");
        ctx.response().end(new JsonObject()
          .put("resources", resources)
          .put("total", listing.total())
          .put("limit", listing.limit())
          .put("offset", listing.offset())
          .encode());
        audit.log(access.resolveActor(ctx), "mcp.resources.list", "/mcp/resources", ctx.request(), 200,
          new JsonObject().put("limit", listing.limit()).put("offset", listing.offset()));
      });

      context.router.get("/mcp/page/*").handler(ctx -> {
        if (!access.authorize(ctx)) {
          return;
        }
        String rawPath = pathAfter(ctx.request().path(), "/mcp/page/");
        McpService.PageDetails details = service.readPage(rawPath);
        if (details == null) {
          ctx.response().setStatusCode(404).end("Page not found");
          audit.log(access.resolveActor(ctx), "mcp.page.read", rawPath, ctx.request(), 404, new JsonObject());
          return;
        }
        ctx.response().putHeader("Content-Type", "application/json");
        ctx.response().end(new JsonObject()
          .put("path", details.path())
          .put("name", details.name())
          .put("content", details.content())
          .put("lastModified", details.lastModified())
          .encode());
        audit.log(access.resolveActor(ctx), "mcp.page.read", details.path(), ctx.request(), 200, new JsonObject());
      });

      context.router.get("/mcp/search").handler(ctx -> {
        if (!access.authorize(ctx)) {
          return;
        }
        String query = ctx.request().getParam("q");
        int limit = parseInt(ctx.request().getParam("limit"), 50);
        int offset = parseInt(ctx.request().getParam("offset"), 0);
        McpService.SearchListing listing = service.search(query, limit, offset);
        JsonArray results = new JsonArray();
        for (SearchResult result : listing.results()) {
          results.add(new JsonObject()
            .put("path", result.path())
            .put("snippet", result.snippet()));
        }
        ctx.response().putHeader("Content-Type", "application/json");
        ctx.response().end(new JsonObject()
          .put("results", results)
          .put("limit", listing.limit())
          .put("offset", listing.offset())
          .encode());
        audit.log(access.resolveActor(ctx), "mcp.search", "/mcp/search", ctx.request(), 200,
          new JsonObject().put("query", query).put("limit", listing.limit()).put("offset", listing.offset()));
      });

      if (context.config.mcpWebSocketEnabled()) {
        context.router.get("/mcp/ws").handler(ctx -> {
          if (!access.authorize(ctx)) {
            return;
          }
          ctx.request().toWebSocket(ar -> {
            if (ar.failed()) {
              return;
            }
            ServerWebSocket socket = ar.result();
            String actor = access.resolveActor(ctx);
            socket.textMessageHandler(message -> handleWebSocketMessage(socket, actor, message, service, audit));
          });
        });
      }

      if (context.config.mcpGrpcEnabled()) {
        McpGrpcServer.start(service, context.fitnesseContext.authenticator, context.config.authEnabled(),
          context.config.mcpGrpcPort(), context.vertx, context.config.rootPath(), context.config.rootDirectory());
      }
    }

    private static String pathAfter(String path, String prefix) {
      if (path == null) {
        return "";
      }
      if (path.startsWith(prefix)) {
        return path.substring(prefix.length());
      }
      return path;
    }

    private static int parseInt(String value, int fallback) {
      if (value == null || value.isEmpty()) {
        return fallback;
      }
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }

    private static void handleWebSocketMessage(ServerWebSocket socket, String actor, String message, McpService service,
                                               McpAuditLogger audit) {
      JsonObject payload;
      try {
        payload = new JsonObject(message);
      } catch (Exception ex) {
        socket.writeTextMessage(new JsonObject()
          .put("type", "error")
          .put("message", "Invalid JSON payload")
          .encode());
        return;
      }
      String type = payload.getString("type", "");
      String requestId = payload.getString("requestId", "");
      switch (type) {
        case "health":
          socket.writeTextMessage(new JsonObject()
            .put("type", "health_response")
            .put("requestId", requestId)
            .put("status", "ok")
            .encode());
          audit.logWebSocket(actor, "mcp.ws.health", "/mcp/ws", 200, new JsonObject());
          break;
        case "list":
          int limit = payload.getInteger("limit", 50);
          int offset = payload.getInteger("offset", 0);
          McpService.PageListing listing = service.listPages(limit, offset);
          JsonArray resources = new JsonArray();
          for (McpService.PageSummary summary : listing.pages()) {
            resources.add(new JsonObject()
              .put("id", summary.path())
              .put("name", summary.name())
              .put("uri", "/mcp/page/" + summary.path())
              .put("type", "wiki-page"));
          }
          socket.writeTextMessage(new JsonObject()
            .put("type", "list_response")
            .put("requestId", requestId)
            .put("resources", resources)
            .put("total", listing.total())
            .put("limit", listing.limit())
            .put("offset", listing.offset())
            .encode());
          audit.logWebSocket(actor, "mcp.ws.resources.list", "/mcp/ws", 200,
            new JsonObject().put("limit", listing.limit()).put("offset", listing.offset()));
          break;
        case "read":
          String path = payload.getString("path", "");
          McpService.PageDetails details = service.readPage(path);
          if (details == null) {
            socket.writeTextMessage(new JsonObject()
              .put("type", "read_response")
              .put("requestId", requestId)
              .put("error", "Page not found")
              .encode());
            audit.logWebSocket(actor, "mcp.ws.page.read", path, 404, new JsonObject());
            break;
          }
          socket.writeTextMessage(new JsonObject()
            .put("type", "read_response")
            .put("requestId", requestId)
            .put("path", details.path())
            .put("name", details.name())
            .put("content", details.content())
            .put("lastModified", details.lastModified())
            .encode());
          audit.logWebSocket(actor, "mcp.ws.page.read", details.path(), 200, new JsonObject());
          break;
        case "search":
          String query = payload.getString("q", "");
          int searchLimit = payload.getInteger("limit", 50);
          int searchOffset = payload.getInteger("offset", 0);
          McpService.SearchListing searchListing = service.search(query, searchLimit, searchOffset);
          JsonArray results = new JsonArray();
          for (SearchResult result : searchListing.results()) {
            results.add(new JsonObject()
              .put("path", result.path())
              .put("snippet", result.snippet()));
          }
          socket.writeTextMessage(new JsonObject()
            .put("type", "search_response")
            .put("requestId", requestId)
            .put("results", results)
            .put("limit", searchListing.limit())
            .put("offset", searchListing.offset())
            .encode());
          audit.logWebSocket(actor, "mcp.ws.search", "/mcp/ws", 200,
            new JsonObject().put("query", query).put("limit", searchListing.limit()).put("offset", searchListing.offset()));
          break;
        default:
          socket.writeTextMessage(new JsonObject()
            .put("type", "error")
            .put("requestId", requestId)
            .put("message", "Unsupported message type")
            .encode());
      }
    }
  }
}
