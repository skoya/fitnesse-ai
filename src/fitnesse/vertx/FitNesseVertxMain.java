package fitnesse.vertx;

import fitnesse.ContextConfigurator;
import fitnesse.ConfigurationParameter;
import fitnesse.FitNesseContext;
import fitnesse.authentication.PromiscuousAuthenticator;
import fitnesse.components.ComponentFactory;
import fitnesse.plugins.PluginException;
import fitnesse.plugins.PluginsLoader;
import fitnesse.responders.WikiPageResponder;
import fitnesse.responders.editing.EditResponder;
import fitnesse.responders.editing.SaveResponder;
import fitnesse.responders.files.UploadResponder;
import fitnesse.responders.testHistory.ExecutionLogResponder;
import fitnesse.ai.AiAssistantService;
import fitnesse.ai.AiEvalService;
import fitnesse.ai.AiHistoryStore;
import fitnesse.ai.AiWorkflowService;
import fitnesse.ai.EchoAiProvider;
import fitnesse.ai.OpenAiProvider;
import fitnesse.search.SearchResult;
import fitnesse.search.SearchService;
import fitnesse.util.ClassUtils;
import io.vertx.core.Vertx;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FitNesseVertxMain {
  private static final Logger LOG = Logger.getLogger(FitNesseVertxMain.class.getName());

  public static void main(String[] args) throws Exception {
    Vertx vertx = createVertx();
    VertxConfig config = VertxConfigLoader.load(vertx);
    FitNesseContext context = buildContext(config);
    startServer(vertx, config, context);
  }

  private static FitNesseContext buildContext(VertxConfig config) throws IOException, PluginException {
    ContextConfigurator configurator = ContextConfigurator.systemDefaults()
      .withRootPath(config.rootPath())
      .withRootDirectoryName(config.rootDirectory());
    configurator.withParameter("wiki.root", "/wiki/");

    File configFile = Paths.get(config.rootPath(), ContextConfigurator.DEFAULT_CONFIG_FILE).toFile();
    configurator.updatedWith(ConfigurationParameter.loadProperties(configFile));

    if (!config.authEnabled()) {
      configurator.withAuthenticator(new PromiscuousAuthenticator());
      LOG.warning("FITNESSE_AUTH_ENABLED=false: auth is disabled for this Vert.x server.");
    }

    return configurator.makeFitNesseContext();
  }

  public static void startServer(Vertx vertx, VertxConfig config, FitNesseContext context) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route().handler(TimeoutHandler.create(config.requestTimeoutMillis()));
    router.route().handler(new VertxIdentityHandler());

    EventBus bus = vertx.eventBus();
    ResponderBusService busService = new ResponderBusService(vertx, context);
    RunMonitor runMonitor = new RunMonitor(snapshot -> bus.publish("fitnesse.run.monitor", snapshot));
    busService.register(bus, "fitnesse.page.view", new WikiPageResponder());
    busService.register(bus, "fitnesse.page.edit", new EditResponder());
    busService.register(bus, "fitnesse.page.save", new SaveResponder());
    busService.register(bus, "fitnesse.page.attachments", new UploadResponder());
    busService.register(bus, "fitnesse.results", new ExecutionLogResponder());
    GitBusService gitBusService = new GitBusService(vertx, Paths.get(config.rootPath(), config.rootDirectory()));
    gitBusService.register(bus);
    SearchService searchService = new SearchService(context.getRootPage());
    SearchBusService searchBusService = new SearchBusService(vertx, searchService);
    searchBusService.register(bus);
    AiAssistantService aiService = new AiAssistantService(buildAiProvider(vertx),
      new AiHistoryStore(vertx, Paths.get(config.rootPath(), config.rootDirectory())));
    AiBusService aiBusService = new AiBusService(vertx, aiService);
    aiBusService.register(bus);
    AiEvalService aiEvalService = new AiEvalService(buildAiProvider(vertx));
    AiEvalBusService aiEvalBusService = new AiEvalBusService(vertx, aiEvalService);
    aiEvalBusService.register(bus);
    AiWorkflowService workflowService = new AiWorkflowService(vertx, aiService,
      Paths.get(config.rootPath(), config.rootDirectory()));
    AiWorkflowBusService workflowBusService = new AiWorkflowBusService(workflowService);
    workflowBusService.register(bus);
    VertxAuthHandler authHandler = null;
    VertxOidcAuthHandler oidcHandler = null;
    if (config.authEnabled()) {
      String azureTenant = readString("FITNESSE_AZURE_TENANT_ID", null);
      if (azureTenant != null && !azureTenant.isEmpty()) {
        String issuer = "https://login.microsoftonline.com/" + azureTenant + "/v2.0";
        String clientId = readString("FITNESSE_AZURE_CLIENT_ID", config.oidcClientId());
        String audience = readString("FITNESSE_AZURE_AUDIENCE", config.oidcAudience());
        oidcHandler = VertxOidcAuthHandler.create(vertx, issuer, clientId, audience);
      } else if (config.oidcEnabled() && config.oidcIssuer() != null && config.oidcClientId() != null) {
        oidcHandler = VertxOidcAuthHandler.create(vertx, config);
      } else {
        authHandler = new VertxAuthHandler(context.authenticator);
      }
    }
    AccessPolicyResolver accessPolicy = new AccessPolicyResolver(
      Paths.get(config.rootPath(), config.rootDirectory()), vertx.fileSystem());
    Handler<RoutingContext> authForPolicy = oidcHandler != null ? oidcHandler : authHandler;
    router.route().handler(new VertxPolicyHandler(accessPolicy, authForPolicy));

    router.get("/").handler(ctx -> ctx.response().setStatusCode(302).putHeader("Location", "/wiki/FrontPage").end());

    router.get("/wiki/*").handler(ctx -> {
      String resource = resourceFrom(pathAfter(ctx.request().path(), "/wiki/"));
      String address = resolvePageAddress(ctx);
      io.vertx.core.json.JsonObject payload = busService.buildPayload(ctx, resource);
      bus.request(address, payload, deliveryOptions(address), ar -> {
        if (ar.succeeded()) {
          io.vertx.core.json.JsonObject response = (io.vertx.core.json.JsonObject) ar.result().body();
          maybeWriteTestArtifacts(vertx, context, address, payload, response);
          busService.writeResponse(ctx, response);
        } else {
          ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
        }
      });
    });
    router.get("/wiki/*/edit").handler(ctx -> {
      String resource = resourceFrom(stripSuffix(pathAfter(ctx.request().path(), "/wiki/"), "/edit"));
      bus.request("fitnesse.page.edit", busService.buildPayload(ctx, resource), deliveryOptions("fitnesse.page.edit"), ar -> {
        if (ar.succeeded()) {
          busService.writeResponse(ctx, (io.vertx.core.json.JsonObject) ar.result().body());
        } else {
          ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
        }
      });
    });
    router.post("/wiki/*").handler(ctx -> {
      String resource = resourceFrom(pathAfter(ctx.request().path(), "/wiki/"));
      bus.request("fitnesse.page.save", busService.buildPayload(ctx, resource), deliveryOptions("fitnesse.page.save"), ar -> {
        if (ar.succeeded()) {
          busService.writeResponse(ctx, (io.vertx.core.json.JsonObject) ar.result().body());
        } else {
          ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
        }
      });
    });
    router.post("/wiki/*/attachments").handler(ctx -> {
      String resource = resourceWithFiles(resourceFrom(stripSuffix(pathAfter(ctx.request().path(), "/wiki/"), "/attachments")));
      bus.request("fitnesse.page.attachments", busService.buildPayload(ctx, resource), deliveryOptions("fitnesse.page.attachments"), ar -> {
        if (ar.succeeded()) {
          busService.writeResponse(ctx, (io.vertx.core.json.JsonObject) ar.result().body());
        } else {
          ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
        }
      });
    });
    router.post("/run").handler(ctx -> {
      String resource = resourceFrom(ctx.request().getParam("suite"));
      io.vertx.core.json.JsonObject payload = busService.buildPayload(ctx, resource);
      io.vertx.core.json.JsonObject params = payload.getJsonObject(ResponderBusService.HEADER_PARAMS, new io.vertx.core.json.JsonObject());
      if (!params.containsKey("format")) {
        params.put("format", new io.vertx.core.json.JsonArray().add("junit"));
      }
      if (!params.containsKey("includehtml")) {
        params.put("includehtml", new io.vertx.core.json.JsonArray().add("true"));
      }
      payload.put(ResponderBusService.HEADER_PARAMS, params);
      bus.request("fitnesse.test.suite", payload, deliveryOptions("fitnesse.test.suite"), ar -> {
        if (ar.succeeded()) {
          io.vertx.core.json.JsonObject response = (io.vertx.core.json.JsonObject) ar.result().body();
          maybeWriteTestArtifacts(vertx, context, "fitnesse.test.suite", payload, response);
          busService.writeResponse(ctx, response);
        } else {
          ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
        }
      });
    });
    router.get("/results/*").handler(ctx -> {
      String resource = resourceFrom(pathAfter(ctx.request().path(), "/results/"));
      bus.request("fitnesse.results", busService.buildPayload(ctx, resource), deliveryOptions("fitnesse.results"), ar -> {
        if (ar.succeeded()) {
          busService.writeResponse(ctx, (io.vertx.core.json.JsonObject) ar.result().body());
        } else {
          ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
        }
      });
    });

    router.get("/search").handler(ctx -> {
      String query = ctx.request().getParam("q");
      String type = ctx.request().getParam("type");
      int limit = parseInt(ctx.request().getParam("limit"), 50);
      int offset = parseInt(ctx.request().getParam("offset"), 0);
      String tags = ctx.request().getParam("tags");
      String pageType = ctx.request().getParam("pageType");
      SearchService.Mode mode = "title".equalsIgnoreCase(type) ? SearchService.Mode.TITLE : SearchService.Mode.CONTENT;
      if (query == null || query.isEmpty()) {
        ctx.response().putHeader("Content-Type", "text/html; charset=UTF-8");
        ctx.response().end(renderSearchForm("", mode, tags, pageType, limit, offset));
        return;
      }
      bus.request(SearchBusService.ADDRESS_SEARCH, new io.vertx.core.json.JsonObject()
        .put("query", query)
        .put("type", mode.name().toLowerCase())
        .put("limit", limit)
        .put("offset", offset)
        .put("tags", tags == null ? "" : tags)
        .put("pageType", pageType == null ? "" : pageType), deliveryOptions(SearchBusService.ADDRESS_SEARCH), ar -> {
          if (ar.succeeded()) {
            io.vertx.core.json.JsonObject body = (io.vertx.core.json.JsonObject) ar.result().body();
            List<SearchResult> results = SearchBusService.toResults(body.getJsonArray("results"));
            ctx.response().putHeader("Content-Type", "text/html; charset=UTF-8");
            ctx.response().end(renderSearchResults(query, mode, tags, pageType, results, limit, offset));
          } else {
            ctx.response().setStatusCode(500).end("Search error: " + ar.cause().getMessage());
          }
        });
    });

    router.get("/history/*").handler(ctx -> {
      String resource = resourceFrom(pathAfter(ctx.request().path(), "/history/"));
      int limit = parseInt(ctx.request().getParam("limit"), 50);
      bus.request(GitBusService.ADDRESS_HISTORY, new io.vertx.core.json.JsonObject()
        .put("path", resource)
        .put("limit", limit), deliveryOptions(GitBusService.ADDRESS_HISTORY), ar -> {
          if (ar.succeeded()) {
            io.vertx.core.json.JsonObject body = (io.vertx.core.json.JsonObject) ar.result().body();
            String html = renderHistoryHtml(resource, body.getJsonArray("entries"));
            ctx.response().putHeader("Content-Type", "text/html; charset=UTF-8");
            ctx.response().end(html);
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });
    router.get("/diff/*").handler(ctx -> {
      String resource = resourceFrom(pathAfter(ctx.request().path(), "/diff/"));
      String commitId = ctx.request().getParam("commitId");
      bus.request(GitBusService.ADDRESS_DIFF, new io.vertx.core.json.JsonObject()
        .put("path", resource)
        .put("commitId", commitId), deliveryOptions(GitBusService.ADDRESS_DIFF), ar -> {
          if (ar.succeeded()) {
            io.vertx.core.json.JsonObject body = (io.vertx.core.json.JsonObject) ar.result().body();
            String html = renderDiffHtml(resource, commitId, body.getString("diff", ""));
            ctx.response().putHeader("Content-Type", "text/html; charset=UTF-8");
            ctx.response().end(html);
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });
    router.post("/revert/*").handler(ctx -> {
      String resource = resourceFrom(pathAfter(ctx.request().path(), "/revert/"));
      String commitId = ctx.request().getParam("commitId");
      bus.request(GitBusService.ADDRESS_REVERT, new io.vertx.core.json.JsonObject()
        .put("path", resource)
        .put("commitId", commitId), deliveryOptions(GitBusService.ADDRESS_REVERT), ar -> {
          if (ar.succeeded()) {
            ctx.response().setStatusCode(302).putHeader("Location", "/history/" + resource).end();
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
      });
    });

    router.get("/api/search").handler(ctx -> {
      String query = ctx.request().getParam("q");
      String type = ctx.request().getParam("type");
      int limit = parseInt(ctx.request().getParam("limit"), 50);
      int offset = parseInt(ctx.request().getParam("offset"), 0);
      String tags = ctx.request().getParam("tags");
      String pageType = ctx.request().getParam("pageType");
      SearchService.Mode mode = "title".equalsIgnoreCase(type) ? SearchService.Mode.TITLE : SearchService.Mode.CONTENT;
      bus.request(SearchBusService.ADDRESS_SEARCH, new io.vertx.core.json.JsonObject()
        .put("query", query == null ? "" : query)
        .put("type", mode.name().toLowerCase())
        .put("tags", tags == null ? "" : tags)
        .put("pageType", pageType == null ? "" : pageType)
        .put("offset", offset)
        .put("limit", limit), deliveryOptions(SearchBusService.ADDRESS_SEARCH), ar -> {
          if (ar.succeeded()) {
            io.vertx.core.json.JsonObject body = (io.vertx.core.json.JsonObject) ar.result().body();
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().end(body.encode());
          } else {
            ctx.response().setStatusCode(500).end("Search error: " + ar.cause().getMessage());
          }
        });
    });

    router.post("/api/ai/assist").handler(ctx -> {
      io.vertx.core.json.JsonObject payload = ctx.body().asJsonObject();
      if (payload == null) {
        payload = new io.vertx.core.json.JsonObject();
      }
      bus.request(AiBusService.ADDRESS_ASSIST, payload, deliveryOptions(AiBusService.ADDRESS_ASSIST), ar -> {
        if (ar.succeeded()) {
          ctx.response().putHeader("Content-Type", "application/json");
          ctx.response().end(((io.vertx.core.json.JsonObject) ar.result().body()).encode());
        } else {
          ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
        }
      });
    });

    router.post("/api/ai/evals").handler(ctx -> {
      io.vertx.core.json.JsonObject payload = ctx.body().asJsonObject();
      if (payload == null) {
        payload = new io.vertx.core.json.JsonObject();
      }
      bus.request(AiEvalBusService.ADDRESS_EVAL, payload, deliveryOptions(AiEvalBusService.ADDRESS_EVAL), ar -> {
        if (ar.succeeded()) {
          ctx.response().putHeader("Content-Type", "application/json");
          ctx.response().end(((io.vertx.core.json.JsonObject) ar.result().body()).encode());
        } else {
          ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
        }
      });
    });

    router.get("/agent").handler(ctx -> {
      ctx.response().setStatusCode(302).putHeader("Location", "/agent/index.html").end();
    });
    router.get("/agent/*").handler(StaticHandler.create("fitnesse/resources/agent-ui"));

    router.get("/api/ai/workflows").handler(ctx -> {
      bus.request(AiWorkflowBusService.ADDRESS_LIST, new io.vertx.core.json.JsonObject(),
        deliveryOptions(AiWorkflowBusService.ADDRESS_LIST), ar -> {
          if (ar.succeeded()) {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().end(((io.vertx.core.json.JsonObject) ar.result().body()).encode());
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });
    router.get("/api/ai/workflows/:id").handler(ctx -> {
      String id = ctx.pathParam("id");
      bus.request(AiWorkflowBusService.ADDRESS_GET, new io.vertx.core.json.JsonObject().put("id", id),
        deliveryOptions(AiWorkflowBusService.ADDRESS_GET), ar -> {
          if (ar.succeeded()) {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().end(((io.vertx.core.json.JsonObject) ar.result().body()).encode());
          } else {
            ctx.response().setStatusCode(404).end("Workflow not found");
          }
        });
    });
    router.post("/api/ai/workflows").handler(ctx -> {
      io.vertx.core.json.JsonObject payload = ctx.body().asJsonObject();
      if (payload == null) {
        payload = new io.vertx.core.json.JsonObject();
      }
      bus.request(AiWorkflowBusService.ADDRESS_SAVE, payload,
        deliveryOptions(AiWorkflowBusService.ADDRESS_SAVE), ar -> {
          if (ar.succeeded()) {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().end(((io.vertx.core.json.JsonObject) ar.result().body()).encode());
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });
    router.post("/api/ai/workflows/run").handler(ctx -> {
      io.vertx.core.json.JsonObject payload = ctx.body().asJsonObject();
      if (payload == null) {
        payload = new io.vertx.core.json.JsonObject();
      }
      bus.request(AiWorkflowBusService.ADDRESS_RUN, payload,
        deliveryOptions(AiWorkflowBusService.ADDRESS_RUN), ar -> {
          if (ar.succeeded()) {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().end(((io.vertx.core.json.JsonObject) ar.result().body()).encode());
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });

    router.get("/api/ai/workflows/:id/runs").handler(ctx -> {
      String id = ctx.pathParam("id");
      int limit = parseInt(ctx.request().getParam("limit"), 10);
      bus.request(AiWorkflowBusService.ADDRESS_RUNS, new io.vertx.core.json.JsonObject()
        .put("id", id)
        .put("limit", limit), deliveryOptions(AiWorkflowBusService.ADDRESS_RUNS), ar -> {
          if (ar.succeeded()) {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().end(((io.vertx.core.json.JsonObject) ar.result().body()).encode());
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });

    router.get("/ai").handler(ctx -> {
      ctx.response().putHeader("Content-Type", "text/html; charset=UTF-8");
      ctx.response().end(renderAiPage());
    });

    router.get("/api/history/*").handler(ctx -> {
      String resource = resourceFrom(pathAfter(ctx.request().path(), "/api/history/"));
      int limit = parseInt(ctx.request().getParam("limit"), 50);
      bus.request(GitBusService.ADDRESS_HISTORY, new io.vertx.core.json.JsonObject()
        .put("path", resource)
        .put("limit", limit), deliveryOptions(GitBusService.ADDRESS_HISTORY), ar -> {
          if (ar.succeeded()) {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().end(((io.vertx.core.json.JsonObject) ar.result().body()).encode());
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });
    router.get("/api/diff/*").handler(ctx -> {
      String resource = resourceFrom(pathAfter(ctx.request().path(), "/api/diff/"));
      String commitId = ctx.request().getParam("commitId");
      bus.request(GitBusService.ADDRESS_DIFF, new io.vertx.core.json.JsonObject()
        .put("path", resource)
        .put("commitId", commitId), deliveryOptions(GitBusService.ADDRESS_DIFF), ar -> {
          if (ar.succeeded()) {
            io.vertx.core.json.JsonObject body = (io.vertx.core.json.JsonObject) ar.result().body();
            ctx.response().putHeader("Content-Type", "text/plain; charset=UTF-8");
            ctx.response().end(body.getString("diff", ""));
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });
    router.post("/api/revert/*").handler(ctx -> {
      String resource = resourceFrom(pathAfter(ctx.request().path(), "/api/revert/"));
      String commitId = ctx.request().getParam("commitId");
      bus.request(GitBusService.ADDRESS_REVERT, new io.vertx.core.json.JsonObject()
        .put("path", resource)
        .put("commitId", commitId), deliveryOptions(GitBusService.ADDRESS_REVERT), ar -> {
          if (ar.succeeded()) {
            ctx.response().setStatusCode(204).end();
          } else {
            ctx.response().setStatusCode(500).end("EventBus error: " + ar.cause().getMessage());
          }
        });
    });

    try {
      ComponentFactory componentFactory = new ComponentFactory(context.getProperties(), ClassUtils.getClassLoader());
      PluginsLoader pluginsLoader = new PluginsLoader(componentFactory, ClassUtils.getClassLoader());
      VertxPluginRegistry pluginRegistry = new VertxPluginRegistry();
      pluginsLoader.loadVertxPlugins(pluginRegistry);
      pluginRegistry.registerAll(new VertxPluginContext(vertx, router, bus, context, config));
    } catch (PluginException e) {
      LOG.log(Level.SEVERE, "Failed to load Vert.x plugins", e);
    }

    Path fileRoot = Paths.get(config.rootPath(), config.rootDirectory(), "files");
    router.get("/files/fitnesse/*")
      .handler(StaticHandler.create("fitnesse/resources"));
    router.get("/files/*")
      .handler(StaticHandler.create()
        .setAllowRootFileSystemAccess(true)
        .setWebRoot(fileRoot.toString()));

    WebClient webClient = WebClient.create(vertx);
    String plantUmlProxyTarget = readString("FITNESSE_PLANTUML_PROXY_TARGET", "https://www.plantuml.com/plantuml");
    router.get("/plantuml/:format/:encoded").handler(ctx -> {
      String format = ctx.pathParam("format");
      String encoded = ctx.pathParam("encoded");
      String target = trimTrailingSlash(plantUmlProxyTarget) + "/" + format + "/" + encoded;
      webClient.getAbs(target).send(ar -> {
        if (ar.succeeded()) {
          io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer> response = ar.result();
          String contentType = response.getHeader("Content-Type");
          if (contentType != null) {
            ctx.response().putHeader("Content-Type", contentType);
          }
          ctx.response().setStatusCode(response.statusCode()).end(response.bodyAsBuffer());
        } else {
          ctx.response().setStatusCode(502).end("PlantUML proxy error: " + ar.cause().getMessage());
        }
      });
    });

    // Auth is now enforced via the policy handler; explicit per-path auth not needed.

    router.get("/api/run/monitor").handler(ctx -> {
      ctx.response().putHeader("Content-Type", "application/json");
      ctx.response().end(runMonitor.snapshot().encode());
    });
    SockJSBridgeOptions ebOptions = new SockJSBridgeOptions()
      .addOutboundPermitted(new PermittedOptions().setAddress("fitnesse.run.monitor"));
    SockJSHandler sockJsHandler = SockJSHandler.create(vertx);
    sockJsHandler.bridge(ebOptions);
    router.route("/eventbus/*").handler(sockJsHandler);

    router.get("/metrics").handler(ctx -> {
      PrometheusMeterRegistry registry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();
      if (registry == null) {
        ctx.response().setStatusCode(503).end("metrics not available");
        return;
      }
      String scrape = registry.scrape();
      ctx.response().putHeader("Content-Type", "text/plain; version=0.0.4").end(scrape);
    });

    router.get("/api/run/config").handler(ctx -> {
      io.vertx.core.json.JsonObject cfg = new io.vertx.core.json.JsonObject()
        .put("poolSize", config.testPoolSize())
        .put("maxQueue", config.testMaxQueue())
        .put("requestTimeoutMillis", config.requestTimeoutMillis())
        .put("idleTimeoutSeconds", config.idleTimeoutSeconds());
      ctx.response().putHeader("Content-Type", "application/json").end(cfg.encode());
    });

    router.get("/run-monitor").handler(ctx -> {
      io.vertx.core.json.JsonObject snapshot = runMonitor.snapshot();
      String html = "<html><head><title>Run Monitor</title>" +
        "<style>body{font-family:sans-serif;margin:1.5rem;} .metric{font-size:1.2rem;} .bar{height:12px;background:#eaeaea;border-radius:6px;overflow:hidden;} .bar span{display:block;height:12px;background:#3b82f6;}</style>" +
        "<script>async function refresh(){const res=await fetch('/api/run/monitor');const data=await res.json();document.getElementById('queued').innerText=data.queued;document.getElementById('running').innerText=data.running;document.getElementById('completed').innerText=data.completed;document.getElementById('avg').innerText=data.averageMillis;const total=data.queued+data.running;const pct=total===0?0:Math.min(100,Math.round(data.running/(total)*100));document.getElementById('bar').style.width=pct+'%';} setInterval(refresh,2000);window.onload=refresh;</script>" +
        "</head><body>" +
        "<h2>Run Monitor</h2>" +
        "<div class='metric'>Queued: <span id='queued'>" + snapshot.getInteger("queued") + "</span></div>" +
        "<div class='metric'>Running: <span id='running'>" + snapshot.getInteger("running") + "</span></div>" +
        "<div class='metric'>Completed: <span id='completed'>" + snapshot.getLong("completed") + "</span></div>" +
        "<div class='metric'>Average (ms): <span id='avg'>" + snapshot.getLong("averageMillis") + "</span></div>" +
        "<div class='metric'>Utilization</div><div class='bar'><span id='bar' style='width:0%'></span></div>" +
        "<p>Configured pool size: " + config.testPoolSize() + ", max queue: " + config.testMaxQueue() + "</p>" +
        "</body></html>";
      ctx.response().putHeader("Content-Type", "text/html; charset=UTF-8").end(html);
    });

    DeploymentOptions workerOpts = new DeploymentOptions().setWorker(true);
    vertx.deployVerticle(() -> new TestRunnerVerticle(busService, runMonitor, config), workerOpts)
      .onSuccess(id -> LOG.info("TestRunnerVerticle deployed: " + id))
      .onFailure(err -> LOG.log(Level.SEVERE, "Failed to deploy TestRunnerVerticle", err));

    vertx.createHttpServer(new HttpServerOptions()
        .setIdleTimeout(config.idleTimeoutSeconds())
        .setIdleTimeoutUnit(TimeUnit.SECONDS))
      .requestHandler(router)
      .listen(config.port(), result -> {
        if (result.succeeded()) {
          LOG.info("FitNesse Vert.x server listening on port " + config.port());
        } else {
          LOG.log(Level.SEVERE, "Failed to start FitNesse Vert.x server", result.cause());
        }
      });
  }

  public static Vertx createVertx() {
    MicrometerMetricsOptions metrics = new MicrometerMetricsOptions()
      .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
      .setEnabled(true);
    VertxOptions options = new VertxOptions().setMetricsOptions(metrics);
    return Vertx.vertx(options);
  }

  private static String resourceFrom(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "FrontPage";
    }
    if (raw.startsWith("/")) {
      return raw.substring(1);
    }
    return raw;
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

  private static String stripSuffix(String value, String suffix) {
    if (value == null || suffix == null) {
      return value;
    }
    if (value.endsWith(suffix)) {
      return value.substring(0, value.length() - suffix.length());
    }
    return value;
  }

  private static int parseInt(String value, int fallback) {
    if (value == null || value.isEmpty()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static String resolvePageAddress(io.vertx.ext.web.RoutingContext ctx) {
    io.vertx.core.MultiMap params = ctx.request().params();
    if (params.contains("suite")) {
      return "fitnesse.test.suite";
    }
    if (params.contains("test")) {
      return "fitnesse.test.single";
    }
    return "fitnesse.page.view";
  }

  private static io.vertx.core.eventbus.DeliveryOptions deliveryOptions(String address) {
    io.vertx.core.eventbus.DeliveryOptions options = new io.vertx.core.eventbus.DeliveryOptions();
    if ("fitnesse.test.suite".equals(address) || "fitnesse.test.single".equals(address)) {
      options.setSendTimeout(10 * 60 * 1000L);
    }
    return options;
  }

  private static void maybeWriteTestArtifacts(Vertx vertx,
                                              FitNesseContext context,
                                              String address,
                                              io.vertx.core.json.JsonObject payload,
                                              io.vertx.core.json.JsonObject response) {
    if (!"fitnesse.test.suite".equals(address) && !"fitnesse.test.single".equals(address)) {
      return;
    }
    String resource = payload.getString(ResponderBusService.HEADER_RESOURCE, "");
    if (resource.contains("..")) {
      return;
    }
    vertx.executeBlocking(promise -> {
      try {
        writeTestArtifacts(context, resource, payload, response);
        promise.complete();
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, ar -> {
      if (ar.failed()) {
        LOG.log(Level.WARNING, "Failed to write test artifacts for " + resource, ar.cause());
      }
    });
  }

  private static void writeTestArtifacts(FitNesseContext context,
                                         String resource,
                                         io.vertx.core.json.JsonObject payload,
                                         io.vertx.core.json.JsonObject response) throws Exception {
    java.text.SimpleDateFormat format = fitnesse.reporting.history.PageHistory.getDateFormat();
    String timestamp = format.format(new java.util.Date());
    java.nio.file.Path artifactDir = java.nio.file.Paths.get(
      context.getTestHistoryDirectory().getPath(),
      resource,
      "artifacts",
      timestamp);
    java.nio.file.Files.createDirectories(artifactDir);

    io.vertx.core.json.JsonObject params =
      payload.getJsonObject(ResponderBusService.HEADER_PARAMS, new io.vertx.core.json.JsonObject());
    if (paramHasValue(params, "format", "junit")) {
      byte[] body = java.util.Base64.getDecoder().decode(response.getString("bodyBase64", ""));
      java.nio.file.Files.write(artifactDir.resolve("junit.xml"), body);
    }

    String html = renderLatestHistoryHtml(context, resource);
    if (html != null) {
      java.nio.file.Files.writeString(artifactDir.resolve("report.html"), html, java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  private static boolean paramHasValue(io.vertx.core.json.JsonObject params, String key, String value) {
    io.vertx.core.json.JsonArray values = params.getJsonArray(key, new io.vertx.core.json.JsonArray());
    for (int i = 0; i < values.size(); i++) {
      if (value.equalsIgnoreCase(String.valueOf(values.getValue(i)))) {
        return true;
      }
    }
    return false;
  }

  private static String renderLatestHistoryHtml(FitNesseContext context, String resource) {
    try {
      fitnesse.responders.testHistory.PageHistoryResponder responder =
        new fitnesse.responders.testHistory.PageHistoryResponder();
      fitnesse.http.MockRequest request = new fitnesse.http.MockRequest(resource);
      request.addInput("resultDate", "latest");
      fitnesse.http.Response historyResponse = responder.makeResponse(context, request);
      BufferedResponseSender sender = new BufferedResponseSender();
      historyResponse.sendTo(sender);
      ResponderResponseParser.ParsedResponse parsed =
        ResponderResponseParser.parse(sender.toByteArray());
      return new String(parsed.body, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Unable to render history HTML for " + resource, e);
      return null;
    }
  }

  private static String resourceWithFiles(String resource) {
    if (resource.endsWith("/files/")) {
      return resource;
    }
    if (resource.endsWith("/files")) {
      return resource + "/";
    }
    return resource + "/files/";
  }

  private static String renderHistoryHtml(String resource, io.vertx.core.json.JsonArray entries) {
    StringBuilder html = new StringBuilder();
    html.append("<!doctype html><html><head><meta charset=\"utf-8\">")
      .append("<title>History - ").append(escapeHtml(resource)).append("</title>")
      .append("<style>")
      .append("body{font-family:Arial,Helvetica,sans-serif;margin:24px;background:#f7f5f0;}")
      .append("table{width:100%;border-collapse:collapse;background:#fff;}")
      .append("th,td{border:1px solid #ddd;padding:8px;text-align:left;}")
      .append("th{background:#efe9df;}")
      .append("a{color:#1f6feb;text-decoration:none;}")
      .append("button{margin-left:6px;}")
      .append("form{display:inline;}")
      .append("</style></head><body>");
    html.append("<script>")
      .append("function confirmRevert(commit){return confirm('Revert to ' + commit + '?');}")
      .append("</script>");
    html.append("<h1>History: ").append(escapeHtml(resource)).append("</h1>");
    html.append("<table><thead><tr>")
      .append("<th>Commit</th><th>Author</th><th>Message</th><th>Timestamp</th><th>Actions</th>")
      .append("</tr></thead><tbody>");
    if (entries != null) {
      for (int i = 0; i < entries.size(); i++) {
        io.vertx.core.json.JsonObject entry = entries.getJsonObject(i);
        String commitId = entry.getString("commitId", "");
        html.append("<tr>");
        html.append("<td><code>").append(escapeHtml(commitId)).append("</code></td>");
        html.append("<td>").append(escapeHtml(entry.getString("author", ""))).append("</td>");
        html.append("<td>").append(escapeHtml(entry.getString("message", ""))).append("</td>");
        html.append("<td>").append(escapeHtml(entry.getString("timestamp", ""))).append("</td>");
        html.append("<td>");
        html.append("<a href=\"/diff/").append(escapeHtml(resource)).append("?commitId=")
          .append(escapeHtml(commitId)).append("\">diff</a>");
        html.append(" ");
        html.append("<a href=\"/api/diff/").append(escapeHtml(resource)).append("?commitId=")
          .append(escapeHtml(commitId)).append("\">raw</a>");
        html.append(" ");
        html.append("<form method=\"post\" action=\"/revert/").append(escapeHtml(resource))
          .append("?commitId=").append(escapeHtml(commitId))
          .append("\" onsubmit=\"return confirmRevert('").append(escapeHtml(commitId)).append("');\">")
          .append("<button type=\"submit\">revert</button></form>");
        html.append("</td>");
        html.append("</tr>");
      }
    }
    html.append("</tbody></table>");
    html.append("<p><a href=\"/wiki/").append(escapeHtml(resource)).append("\">Back to page</a></p>");
    html.append("</body></html>");
    return html.toString();
  }

  private static String renderDiffHtml(String resource, String commitId, String diff) {
    StringBuilder html = new StringBuilder();
    html.append("<!doctype html><html><head><meta charset=\"utf-8\">")
      .append("<title>Diff - ").append(escapeHtml(resource)).append("</title>")
      .append("<style>")
      .append("body{font-family:Arial,Helvetica,sans-serif;margin:24px;background:#f7f5f0;}")
      .append("pre{background:#fff;border:1px solid #ddd;padding:12px;white-space:pre-wrap;}")
      .append(".diff-add{background:#e6ffed;}")
      .append(".diff-del{background:#ffeef0;}")
      .append(".diff-hunk{background:#f0f0f0;color:#333;}")
      .append("a{color:#1f6feb;text-decoration:none;}")
      .append("</style></head><body>");
    html.append("<h1>Diff: ").append(escapeHtml(resource)).append("</h1>");
    html.append("<p>Commit: <code>").append(escapeHtml(commitId)).append("</code></p>");
    html.append("<pre>").append(formatDiff(diff)).append("</pre>");
    html.append("<p><a href=\"/history/").append(escapeHtml(resource)).append("\">Back to history</a></p>");
    html.append("</body></html>");
    return html.toString();
  }

  private static String renderSearchForm(String query, SearchService.Mode mode, String tags, String pageType, int limit, int offset) {
    StringBuilder html = new StringBuilder();
    html.append("<!doctype html><html><head><meta charset=\"utf-8\">")
      .append("<title>Search</title>")
      .append("<style>")
      .append("body{font-family:Arial,Helvetica,sans-serif;margin:24px;background:#f7f5f0;}")
      .append("form{background:#fff;padding:16px;border:1px solid #ddd;}")
      .append("input[type=text]{width:60%;padding:6px;}")
      .append("</style></head><body>");
    html.append("<h1>Search</h1>");
    html.append("<form method=\"get\" action=\"/search\">")
      .append("<input type=\"text\" name=\"q\" value=\"").append(escapeHtml(query)).append("\"/>")
      .append("<select name=\"type\">")
      .append("<option value=\"content\"").append(mode == SearchService.Mode.CONTENT ? " selected" : "").append(">Content</option>")
      .append("<option value=\"title\"").append(mode == SearchService.Mode.TITLE ? " selected" : "").append(">Title</option>")
      .append("</select>")
      .append("<input type=\"text\" name=\"tags\" placeholder=\"tags\" value=\"").append(escapeHtml(tags)).append("\"/>")
      .append("<select name=\"pageType\">")
      .append("<option value=\"any\"").append(isAnyPageType(pageType) ? " selected" : "").append(">Any</option>")
      .append("<option value=\"suite\"").append(isPageType(pageType, "suite") ? " selected" : "").append(">Suite</option>")
      .append("<option value=\"test\"").append(isPageType(pageType, "test") ? " selected" : "").append(">Test</option>")
      .append("</select>")
      .append("<input type=\"number\" name=\"limit\" min=\"1\" max=\"200\" value=\"").append(limit).append("\"/>")
      .append("<input type=\"hidden\" name=\"offset\" value=\"").append(offset).append("\"/>")
      .append("<button type=\"submit\">Search</button>")
      .append("</form>");
    html.append("</body></html>");
    return html.toString();
  }

  private static String renderSearchResults(String query, SearchService.Mode mode, String tags, String pageType,
                                            List<SearchResult> results, int limit, int offset) {
    StringBuilder html = new StringBuilder();
    html.append(renderSearchForm(query, mode, tags, pageType, limit, offset).replace("</body></html>", ""));
    html.append("<h2>Results (").append(results.size()).append(")</h2>");
    html.append("<ul>");
    for (SearchResult result : results) {
      html.append("<li><a href=\"/wiki/").append(escapeHtml(result.path())).append("\">")
        .append(escapeHtml(result.path())).append("</a>");
      if (result.snippet() != null && !result.snippet().isEmpty()) {
        html.append("<div><code>").append(escapeHtml(result.snippet())).append("</code></div>");
      }
      html.append("</li>");
    }
    html.append("</ul>");
    html.append("<div>");
    int prevOffset = Math.max(0, offset - limit);
    if (offset > 0) {
      html.append("<a href=\"/search?q=").append(urlEncode(query))
        .append("&type=").append(urlEncode(mode.name().toLowerCase()))
        .append("&tags=").append(urlEncode(tags))
        .append("&pageType=").append(urlEncode(pageType))
        .append("&limit=").append(limit)
        .append("&offset=").append(prevOffset)
        .append("\">Prev</a>");
    }
    if (results.size() == limit) {
      if (offset > 0) {
        html.append(" | ");
      }
      html.append("<a href=\"/search?q=").append(urlEncode(query))
        .append("&type=").append(urlEncode(mode.name().toLowerCase()))
        .append("&tags=").append(urlEncode(tags))
        .append("&pageType=").append(urlEncode(pageType))
        .append("&limit=").append(limit)
        .append("&offset=").append(offset + limit)
        .append("\">Next</a>");
    }
    html.append("</div>");
    html.append("</body></html>");
    return html.toString();
  }

  private static boolean isAnyPageType(String pageType) {
    return pageType == null || pageType.isEmpty() || "any".equalsIgnoreCase(pageType);
  }

  private static boolean isPageType(String pageType, String expected) {
    return expected != null && expected.equalsIgnoreCase(pageType);
  }

  private static String urlEncode(String value) {
    if (value == null) {
      return "";
    }
    try {
      return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      return value;
    }
  }

  private static fitnesse.ai.AiProvider buildAiProvider(Vertx vertx) {
    String provider = readString("FITNESSE_AI_PROVIDER", "echo");
    if ("openai".equalsIgnoreCase(provider)) {
      String apiKey = readString("OPENAI_API_KEY", null);
      String model = readString("OPENAI_MODEL", "gpt-4o-mini");
      return new OpenAiProvider(vertx, apiKey, model);
    }
    return new EchoAiProvider();
  }

  private static String readString(String key, String fallback) {
    String value = System.getProperty(key);
    if (value == null || value.isEmpty()) {
      value = System.getenv(key);
    }
    return (value == null || value.isEmpty()) ? fallback : value;
  }

  private static String trimTrailingSlash(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static String renderAiPage() {
    StringBuilder html = new StringBuilder();
    html.append("<!doctype html><html><head><meta charset=\"utf-8\">")
      .append("<title>AI Assistant</title>")
      .append("<style>")
      .append("body{font-family:Arial,Helvetica,sans-serif;margin:24px;background:#f7f5f0;}")
      .append("textarea{width:100%;height:160px;padding:8px;}")
      .append("input,select{padding:6px;margin-top:6px;}")
      .append("button{margin-top:8px;}")
      .append("pre{background:#fff;border:1px solid #ddd;padding:12px;}")
      .append("</style></head><body>");
    html.append("<h1>AI Assistant</h1>");
    html.append("<label>Tool</label><br/>");
    html.append("<select id=\"tool\"><option value=\"assist\">Assist</option><option value=\"test-gen\">Generate Test</option></select><br/>");
    html.append("<input id=\"pagePath\" placeholder=\"Page path (for test-gen)\"/><br/>");
    html.append("<input id=\"fixture\" placeholder=\"Fixture name (for test-gen)\"/><br/>");
    html.append("<textarea id=\"prompt\" placeholder=\"Ask for help...\"></textarea>");
    html.append("<br/><button id=\"send\">Send</button>");
    html.append("<h2>Response</h2><pre id=\"response\"></pre>");
    html.append("<script>")
      .append("document.getElementById('send').onclick=function(){")
      .append("var prompt=document.getElementById('prompt').value;")
      .append("var tool=document.getElementById('tool').value;")
      .append("var pagePath=document.getElementById('pagePath').value;")
      .append("var fixture=document.getElementById('fixture').value;")
      .append("var params={}; if(pagePath){params.pagePath=pagePath;} if(fixture){params.fixture=fixture;}")
      .append("fetch('/api/ai/assist',{method:'POST',headers:{'Content-Type':'application/json'},")
      .append("body:JSON.stringify({prompt:prompt,tool:tool,params:params})})")
      .append(".then(r=>r.json()).then(j=>{document.getElementById('response').textContent=j.response||'';});")
      .append("};")
      .append("</script>");
    html.append("</body></html>");
    return html.toString();
  }

  private static String formatDiff(String diff) {
    if (diff == null || diff.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    String[] lines = diff.split("\\r?\\n", -1);
    for (String line : lines) {
      String escaped = escapeHtml(line);
      if (line.startsWith("+") && !line.startsWith("+++")) {
        out.append("<span class=\"diff-add\">").append(escaped).append("</span>");
      } else if (line.startsWith("-") && !line.startsWith("---")) {
        out.append("<span class=\"diff-del\">").append(escaped).append("</span>");
      } else if (line.startsWith("@@")) {
        out.append("<span class=\"diff-hunk\">").append(escaped).append("</span>");
      } else {
        out.append(escaped);
      }
      out.append("\n");
    }
    return out.toString();
  }

  private static String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;");
  }

}
