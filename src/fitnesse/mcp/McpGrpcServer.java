package fitnesse.mcp;

import fitnesse.authentication.Authenticator;
import fitnesse.mcp.proto.HealthRequest;
import fitnesse.mcp.proto.HealthResponse;
import fitnesse.mcp.proto.ListResourcesRequest;
import fitnesse.mcp.proto.ListResourcesResponse;
import fitnesse.mcp.proto.ReadPageRequest;
import fitnesse.mcp.proto.ReadPageResponse;
import fitnesse.mcp.proto.SearchRequest;
import fitnesse.mcp.proto.SearchResponse;
import fitnesse.mcp.proto.VertxFitnesseMcpGrpc;
import fitnesse.search.SearchResult;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.grpc.VertxServer;
import io.vertx.grpc.VertxServerBuilder;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the MCP gRPC adapter with basic auth and audit logging.
 */
public final class McpGrpcServer {
  private static final Logger LOG = Logger.getLogger(McpGrpcServer.class.getName());
  private static VertxServer server;

  private McpGrpcServer() {
  }

  public static synchronized void start(McpService service, Authenticator authenticator, boolean authEnabled,
                                        int port, Vertx vertx, String rootPath, String rootDirectory) {
    if (server != null) {
      return;
    }
    McpAuditLogger audit = new McpAuditLogger(vertx, rootPath, rootDirectory);
    McpGrpcAuthInterceptor authInterceptor = new McpGrpcAuthInterceptor(authEnabled, authenticator);
    VertxFitnesseMcpGrpc.FitnesseMcpVertxImplBase grpcService = new McpGrpcService(service, audit, authInterceptor);
    ServerServiceDefinition intercepted = ServerInterceptors.intercept(grpcService, authInterceptor);
    server = VertxServerBuilder
      .forAddress(vertx, "0.0.0.0", port)
      .addService(intercepted)
      .build();
    Promise<Void> startPromise = Promise.promise();
    server.start(startPromise);
    startPromise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        LOG.info("MCP gRPC server listening on port " + port);
      } else {
        LOG.log(Level.SEVERE, "Failed to start MCP gRPC server", ar.cause());
      }
    });
  }

  private static final class McpGrpcService extends VertxFitnesseMcpGrpc.FitnesseMcpVertxImplBase {
    private final McpService service;
    private final McpAuditLogger audit;
    private final McpGrpcAuthInterceptor authInterceptor;

    private McpGrpcService(McpService service, McpAuditLogger audit, McpGrpcAuthInterceptor authInterceptor) {
      this.service = service;
      this.audit = audit;
      this.authInterceptor = authInterceptor;
    }

    @Override
    public Future<HealthResponse> health(HealthRequest request) {
      HealthResponse response = HealthResponse.newBuilder()
        .setStatus("ok")
        .build();
      audit.logGrpc(actor(), "mcp.grpc.health", "health", 200, new io.vertx.core.json.JsonObject());
      return Future.succeededFuture(response);
    }

    @Override
    public Future<ListResourcesResponse> listResources(ListResourcesRequest request) {
      int limit = request.getLimit() <= 0 ? 50 : request.getLimit();
      int offset = Math.max(0, request.getOffset());
      McpService.PageListing listing = service.listPages(limit, offset);
      ListResourcesResponse.Builder builder = ListResourcesResponse.newBuilder()
        .setTotal(listing.total())
        .setLimit(listing.limit())
        .setOffset(listing.offset());
      for (McpService.PageSummary summary : listing.pages()) {
        builder.addResources(fitnesse.mcp.proto.Resource.newBuilder()
          .setId(summary.path())
          .setName(summary.name())
          .setUri("/mcp/page/" + summary.path())
          .setType("wiki-page")
          .build());
      }
      ListResourcesResponse resp = builder.build();
      audit.logGrpc(actor(), "mcp.grpc.resources.list", "resources", 200,
        new io.vertx.core.json.JsonObject().put("limit", listing.limit()).put("offset", listing.offset()));
      return Future.succeededFuture(resp);
    }

    @Override
    public Future<ReadPageResponse> readPage(ReadPageRequest request) {
      McpService.PageDetails details = service.readPage(request.getPath());
      ReadPageResponse.Builder builder = ReadPageResponse.newBuilder();
      if (details == null) {
        builder.setFound(false).setError("Page not found");
        audit.logGrpc(actor(), "mcp.grpc.page.read", request.getPath(), 404, new io.vertx.core.json.JsonObject());
        return Future.succeededFuture(builder.build());
      }
      builder.setFound(true)
        .setPath(details.path())
        .setName(details.name())
        .setContent(details.content())
        .setLastModified(details.lastModified() == null ? "" : details.lastModified());
      ReadPageResponse resp = builder.build();
      audit.logGrpc(actor(), "mcp.grpc.page.read", details.path(), 200, new io.vertx.core.json.JsonObject());
      return Future.succeededFuture(resp);
    }

    @Override
    public Future<SearchResponse> search(SearchRequest request) {
      String query = request.getQuery();
      int limit = request.getLimit() <= 0 ? 50 : request.getLimit();
      int offset = Math.max(0, request.getOffset());
      McpService.SearchListing listing = service.search(query, limit, offset);
      SearchResponse.Builder builder = SearchResponse.newBuilder()
        .setLimit(listing.limit())
        .setOffset(listing.offset());
      List<SearchResult> results = listing.results();
      for (SearchResult result : results) {
        builder.addResults(fitnesse.mcp.proto.SearchResult.newBuilder()
          .setPath(result.path())
          .setSnippet(result.snippet())
          .build());
      }
      SearchResponse resp = builder.build();
      audit.logGrpc(actor(), "mcp.grpc.search", "search", 200,
        new io.vertx.core.json.JsonObject().put("query", query).put("limit", listing.limit()).put("offset", listing.offset()));
      return Future.succeededFuture(resp);
    }

    private String actor() {
      String actor = authInterceptor.currentActor();
      return actor == null ? "anonymous" : actor;
    }
  }
}
