package fitnesse.vertx;

import fitnesse.FitNesseContext;
import fitnesse.Responder;
import fitnesse.http.MockRequest;
import fitnesse.http.Request;
import fitnesse.http.Response;
import fitnesse.http.UploadedFile;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Adapts legacy FitNesse responders into async Vert.x EventBus handlers.
 * Uses executeBlocking to keep responder work off the event loop.
 */
final class ResponderBusService {
  static final String HEADER_RESOURCE = "resource";
  static final String HEADER_CONTEXT_ROOT = "contextRoot";
  static final String HEADER_HEADERS = "headers";
  static final String HEADER_PARAMS = "params";
  static final String HEADER_BODY = "body";
  static final String HEADER_UPLOADS = "uploads";

  private final Vertx vertx;
  private final FitNesseContext context;

  ResponderBusService(Vertx vertx, FitNesseContext context) {
    this.vertx = vertx;
    this.context = context;
  }

  /**
   * Registers a responder on the EventBus and returns serialized HTTP metadata.
   */
  void register(EventBus bus, String address, Responder responder) {
    register(bus, address, responder, null, null, -1);
  }

  /**
   * Registers using a dedicated worker executor and optional run monitor/backpressure.
   */
  void registerWithExecutor(EventBus bus, String address, Responder responder,
                            WorkerExecutor executor, RunMonitor monitor, int maxQueue) {
    register(bus, address, responder, executor, monitor, maxQueue);
  }

  private void register(EventBus bus, String address, Responder responder,
                        WorkerExecutor executor, RunMonitor monitor, int maxQueue) {
    bus.consumer(address, message -> {
      JsonObject payload = (JsonObject) message.body();
      if (monitor != null) {
        if (!monitor.canAccept(maxQueue)) {
          message.fail(429, "Test queue is full");
          return;
        }
        monitor.incrementQueued();
      }
      io.vertx.core.Handler<io.vertx.core.Promise<JsonObject>> work = promise -> {
        long start = monitor == null ? 0 : monitor.startRun();
        try {
          promise.complete(handle(payload, responder));
        } catch (Exception e) {
          promise.fail(e);
        } finally {
          if (monitor != null) {
            monitor.finishRun(start);
          }
        }
      };

      if (executor != null) {
        executor.executeBlocking(work, false, ar -> {
          if (ar.succeeded()) {
            message.reply(ar.result());
          } else {
            message.fail(500, ar.cause().getMessage());
          }
        });
      } else {
        vertx.executeBlocking(work, false, ar -> {
          if (ar.succeeded()) {
            message.reply(ar.result());
          } else {
            message.fail(500, ar.cause().getMessage());
          }
        });
      }
    });
  }

  /**
   * Creates a bus payload from the Vert.x routing context.
   */
  JsonObject buildPayload(RoutingContext routingContext, String resource) {
    JsonObject payload = new JsonObject();
    payload.put(HEADER_RESOURCE, resource);
    payload.put(HEADER_CONTEXT_ROOT, context.contextRoot);
    payload.put(HEADER_HEADERS, multimapToJson(routingContext.request().headers()));
    payload.put(HEADER_PARAMS, multimapToJson(routingContext.request().params()));
    payload.put(HEADER_BODY, routingContext.getBodyAsString());

    JsonArray uploads = new JsonArray();
    for (FileUpload upload : routingContext.fileUploads()) {
      JsonObject entry = new JsonObject();
      entry.put("name", upload.name());
      entry.put("fileName", upload.fileName());
      entry.put("contentType", upload.contentType());
      entry.put("path", upload.uploadedFileName());
      uploads.add(entry);
    }
    payload.put(HEADER_UPLOADS, uploads);
    return payload;
  }

  /**
   * Writes a responder payload to the HTTP response.
   */
  void writeResponse(RoutingContext routingContext, JsonObject response) {
    int status = response.getInteger("status", 200);
    routingContext.response().setStatusCode(status);

    JsonObject headers = response.getJsonObject("headers", new JsonObject());
    for (String name : headers.fieldNames()) {
      if (!isValidHeaderName(name)) {
        continue;
      }
      if ("Transfer-Encoding".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) {
        continue;
      }
      try {
        routingContext.response().putHeader(name, headers.getString(name));
      } catch (IllegalArgumentException e) {
        // Ignore invalid headers from legacy responders.
      }
    }
    if (!headers.containsKey("ETag") && headers.containsKey("Current-Version")) {
      routingContext.response().putHeader("ETag", headers.getString("Current-Version"));
    }

    String bodyBase64 = response.getString("bodyBase64", "");
    byte[] body = bodyBase64.isEmpty() ? new byte[0] : Base64.getDecoder().decode(bodyBase64);
    if ("chunked".equalsIgnoreCase(headers.getString("Transfer-Encoding"))) {
      body = decodeChunked(body);
    }
    routingContext.response().putHeader("Content-Length", String.valueOf(body.length));
    if (body.length == 0) {
      routingContext.response().end();
    } else {
      routingContext.response().end(Buffer.buffer(body));
    }
  }

  /**
   * Executes the responder and normalizes output to JSON payload.
   */
  private JsonObject handle(JsonObject payload, Responder responder) {
    String resource = payload.getString(HEADER_RESOURCE, "");
    MockRequest request = new MockRequest(resource);
    request.addInput(Request.NOCHUNK, "true");
    request.setContextRoot(payload.getString(HEADER_CONTEXT_ROOT, context.contextRoot));

    JsonObject headers = payload.getJsonObject(HEADER_HEADERS, new JsonObject());
    for (String name : headers.fieldNames()) {
      JsonArray values = headers.getJsonArray(name, new JsonArray());
      for (Object value : values) {
        request.addHeader(name, String.valueOf(value));
      }
    }

    JsonObject params = payload.getJsonObject(HEADER_PARAMS, new JsonObject());
    for (String name : params.fieldNames()) {
      JsonArray values = params.getJsonArray(name, new JsonArray());
      for (Object value : values) {
        request.addInput(name, String.valueOf(value));
      }
    }

    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Basic ")) {
      String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
      int split = decoded.indexOf(':');
      if (split > 0) {
        request.setCredentials(decoded.substring(0, split), decoded.substring(split + 1));
      }
    }

    JsonArray uploads = payload.getJsonArray(HEADER_UPLOADS, new JsonArray());
    for (int i = 0; i < uploads.size(); i++) {
      JsonObject entry = uploads.getJsonObject(i);
      if (entry == null) {
        continue;
      }
      String name = entry.getString("name", "file");
      String fileName = entry.getString("fileName", "");
      String contentType = entry.getString("contentType", "application/octet-stream");
      String path = entry.getString("path", "");
      if (!path.isEmpty()) {
        UploadedFile uploadedFile = new UploadedFile(fileName, contentType, new java.io.File(path));
        request.addUploadedFile(name, uploadedFile);
      }
    }

    String body = payload.getString(HEADER_BODY, null);
    if (body != null) {
      request.setBody(body);
    }

    try {
      Response response = responder.makeResponse(context, request);
      BufferedResponseSender sender = new BufferedResponseSender();
      response.sendTo(sender);
      ResponderResponseParser.ParsedResponse parsed = ResponderResponseParser.parse(sender.toByteArray());
      JsonObject result = new JsonObject();
      result.put("status", parsed.status);
      JsonObject headerJson = new JsonObject();
      for (Map.Entry<String, String> entry : parsed.headers.entrySet()) {
        headerJson.put(entry.getKey(), entry.getValue());
      }
      result.put("headers", headerJson);
      result.put("bodyBase64", Base64.getEncoder().encodeToString(parsed.body));
      return result;
    } catch (Exception e) {
      JsonObject result = new JsonObject();
      result.put("status", 500);
      result.put("headers", new JsonObject());
      result.put("bodyBase64", Base64.getEncoder().encodeToString(("Responder error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8)));
      return result;
    }
  }

  private JsonObject multimapToJson(MultiMap map) {
    JsonObject json = new JsonObject();
    for (String name : map.names()) {
      JsonArray values = new JsonArray();
      for (String value : map.getAll(name)) {
        values.add(value);
      }
      json.put(name, values);
    }
    return json;
  }

  private static boolean isValidHeaderName(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      boolean ok = (ch >= '0' && ch <= '9')
        || (ch >= 'A' && ch <= 'Z')
        || (ch >= 'a' && ch <= 'z')
        || ch == '-' || ch == '_' || ch == '.' || ch == '!';
      if (!ok) {
        return false;
      }
    }
    return true;
  }

  private static byte[] decodeChunked(byte[] body) {
    int index = 0;
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    while (index < body.length) {
      int lineEnd = findCrlf(body, index);
      if (lineEnd < 0) {
        break;
      }
      String sizeLine = new String(body, index, lineEnd - index, StandardCharsets.US_ASCII).trim();
      int size;
      try {
        size = Integer.parseInt(sizeLine, 16);
      } catch (NumberFormatException e) {
        return body;
      }
      index = lineEnd + 2;
      if (size == 0) {
        break;
      }
      if (index + size > body.length) {
        return body;
      }
      out.write(body, index, size);
      index += size + 2;
    }
    return out.toByteArray();
  }

  private static int findCrlf(byte[] body, int start) {
    for (int i = start; i + 1 < body.length; i++) {
      if (body[i] == '\r' && body[i + 1] == '\n') {
        return i;
      }
    }
    return -1;
  }
}
