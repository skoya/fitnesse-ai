// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.http;

import java.io.IOException;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.StreamReader;

public class ResponseParser {
  private int status;
  private String body;
  private Map<String, String> headers = new HashMap<>();
  private StreamReader input;

  private static final Pattern statusLinePattern = Pattern.compile("HTTP/\\d.\\d (\\d\\d\\d) ");
  private static final Pattern headerPattern = Pattern.compile("([^:]*): (.*)");
  private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
  private static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 10;
  private static final int DEFAULT_REQUEST_TIMEOUT_MS = 30000;
  private static final int DEFAULT_MAX_RETRIES = 3;

  public ResponseParser(InputStream input) throws IOException {
    this.input = new StreamReader(input);
    parseStatusLine();
    parseHeaders();
    if (isChuncked()) {
      parseChunks();
      parseHeaders();
    } else
      parseBody();
  }

  public ResponseParser(int status, String body, Map<String, String> headers) {
    this.status = status;
    this.body = body;
    this.headers = new HashMap<>(headers);
  }

  private boolean isChuncked() {
    String encoding = getHeader("Transfer-Encoding");
    return "chunked".equalsIgnoreCase(encoding);
  }

  private void parseStatusLine() throws IOException {
    String statusLine = input.readLine();
    Matcher match = statusLinePattern.matcher(statusLine);
    if (match.find()) {
      String status = match.group(1);
      this.status = Integer.parseInt(status);
    } else
      throw new IOException("Could not parse Response");
  }

  private void parseHeaders() throws IOException {
    String line = input.readLine();
    while (!"".equals(line)) {
      Matcher match = headerPattern.matcher(line);
      if (match.find()) {
        String key = match.group(1);
        String value = match.group(2);
        headers.put(key, value);
      }
      line = input.readLine();
    }
  }

  private void parseBody() throws IOException {
    String lengthHeader = "Content-Length";
    if (hasHeader(lengthHeader)) {
      int bytesToRead = Integer.parseInt(getHeader(lengthHeader));
      body = input.read(bytesToRead);
    }
  }

  private void parseChunks() throws IOException {
    StringBuilder bodyBuffer = new StringBuilder();
    int chunkSize = readChunkSize();
    while (chunkSize != 0) {
      bodyBuffer.append(input.read(chunkSize));
      readCRLF();
      chunkSize = readChunkSize();
    }
    body = bodyBuffer.toString();

  }

  private int readChunkSize() throws IOException {
    String sizeLine = input.readLine();
    return Integer.parseInt(sizeLine, 16);
  }

  private void readCRLF() throws IOException {
    input.read(2);
  }

  public int getStatus() {
    return status;
  }

  public String getBody() {
    return body;
  }

  public String getHeader(String key) {
    return headers.get(key);
  }

  public boolean hasHeader(String key) {
    return headers.containsKey(key);
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("Status: ").append(status).append("\n");
    buffer.append("Headers: ").append("\n");
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      buffer.append("\t").append(key).append(": ").append(value).append("\n");
    }
    buffer.append("Body: ").append("\n");
    buffer.append(body);
    return buffer.toString();
  }

  public static ResponseParser performHttpRequest(String hostname, int hostPort, RequestBuilder builder) throws IOException {
    Vertx vertx = Vertx.vertx();
    WebClientOptions options = new WebClientOptions()
      .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS)
      .setIdleTimeout(DEFAULT_IDLE_TIMEOUT_SECONDS);
    WebClient client = WebClient.create(vertx, options);
    try {
      return performHttpRequestWithRetries(client, hostname, hostPort, builder);
    } finally {
      client.close();
      vertx.close();
    }
  }

  private static ResponseParser performHttpRequestWithRetries(WebClient client, String hostname, int hostPort, RequestBuilder builder)
    throws IOException {
    IOException lastError = null;
    for (int attempt = 1; attempt <= DEFAULT_MAX_RETRIES; attempt++) {
      try {
        ResponseParser response = performHttpRequestOnce(client, hostname, hostPort, builder);
        if (response.getStatus() >= 500 && attempt < DEFAULT_MAX_RETRIES) {
          sleepRetryBackoff(attempt);
          continue;
        }
        return response;
      } catch (IOException e) {
        lastError = e;
        if (attempt < DEFAULT_MAX_RETRIES) {
          sleepRetryBackoff(attempt);
          continue;
        }
        throw e;
      }
    }
    throw lastError == null ? new IOException("HTTP request failed") : lastError;
  }

  private static ResponseParser performHttpRequestOnce(WebClient client, String hostname, int hostPort, RequestBuilder builder)
    throws IOException {
    String traceId = UUID.randomUUID().toString();
    String resource = builder.getResource();
    String query = builder.inputString();
    if (builder.isGetMethod() && !query.isEmpty()) {
      resource = resource.contains("?") ? resource + "&" + query : resource + "?" + query;
    }

    io.vertx.ext.web.client.HttpRequest<Buffer> request =
      client.request(io.vertx.core.http.HttpMethod.valueOf(builder.getMethod()), hostPort, hostname, resource)
        .timeout(DEFAULT_REQUEST_TIMEOUT_MS)
        .putHeader("X-FitNesse-Trace-Id", traceId);

    for (Map.Entry<String, String> header : builder.getHeaders().entrySet()) {
      request.putHeader(header.getKey(), header.getValue());
    }

    HttpResponse<Buffer> response = sendAndAwait(request);
    Map<String, String> headers = new HashMap<>();
    for (String headerName : response.headers().names()) {
      headers.put(headerName, response.getHeader(headerName));
    }
    String body = response.bodyAsString();
    return new ResponseParser(response.statusCode(), body, headers);
  }

  private static HttpResponse<Buffer> sendAndAwait(io.vertx.ext.web.client.HttpRequest<Buffer> request) throws IOException {
    try {
      return fitnesse.vertx.VertxFutures.await(request.send(), DEFAULT_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      throw new IOException("HTTP request failed", e);
    }
  }

  private static void sleepRetryBackoff(int attempt) {
    try {
      Thread.sleep(200L * attempt);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
