package fitnesse.vertx;

public final class VertxConfig {
  private final int port;
  private final String rootPath;
  private final String rootDirectory;
  private final boolean authEnabled;
  private final boolean mcpWebSocketEnabled;
  private final boolean mcpGrpcEnabled;
  private final int mcpGrpcPort;
  private final int requestTimeoutMillis;
  private final int idleTimeoutSeconds;
  private final int testPoolSize;
  private final int testMaxQueue;
  private final boolean oidcEnabled;
  private final String oidcIssuer;
  private final String oidcClientId;
  private final String oidcAudience;

  private VertxConfig(int port, String rootPath, String rootDirectory, boolean authEnabled,
                      boolean mcpWebSocketEnabled, boolean mcpGrpcEnabled, int mcpGrpcPort,
                      int requestTimeoutMillis, int idleTimeoutSeconds,
                      int testPoolSize, int testMaxQueue,
                      boolean oidcEnabled, String oidcIssuer, String oidcClientId, String oidcAudience) {
    this.port = port;
    this.rootPath = rootPath;
    this.rootDirectory = rootDirectory;
    this.authEnabled = authEnabled;
    this.mcpWebSocketEnabled = mcpWebSocketEnabled;
    this.mcpGrpcEnabled = mcpGrpcEnabled;
    this.mcpGrpcPort = mcpGrpcPort;
    this.requestTimeoutMillis = requestTimeoutMillis;
    this.idleTimeoutSeconds = idleTimeoutSeconds;
    this.testPoolSize = testPoolSize;
    this.testMaxQueue = testMaxQueue;
    this.oidcEnabled = oidcEnabled;
    this.oidcIssuer = oidcIssuer;
    this.oidcClientId = oidcClientId;
    this.oidcAudience = oidcAudience;
  }

  static VertxConfig fromEnv() {
    int port = readInt("FITNESSE_VERTX_PORT", 8080);
    String rootPath = readString("FITNESSE_ROOT_PATH", ".");
    String rootDirectory = readString("FITNESSE_ROOT_DIR", "FitNesseRoot");
    boolean authEnabled = readBoolean("FITNESSE_AUTH_ENABLED", false);
    boolean mcpWebSocketEnabled = readBoolean("FITNESSE_MCP_WS_ENABLED", false);
    boolean mcpGrpcEnabled = readBoolean("FITNESSE_MCP_GRPC_ENABLED", false);
    int mcpGrpcPort = readInt("FITNESSE_MCP_GRPC_PORT", 50051);
    int requestTimeoutMillis = readInt("FITNESSE_HTTP_TIMEOUT_MS", 30_000);
    int idleTimeoutSeconds = readInt("FITNESSE_IDLE_TIMEOUT_SEC", 60);
    int cores = Runtime.getRuntime().availableProcessors();
    int defaultPoolSize = Math.max(2, cores);
    int testPoolSize = readInt("FITNESSE_TEST_POOL_SIZE", defaultPoolSize);
    int defaultQueue = testPoolSize * 4;
    int testMaxQueue = readInt("FITNESSE_TEST_MAX_QUEUE", defaultQueue);
    boolean oidcEnabled = readBoolean("FITNESSE_OIDC_ENABLED", false);
    String oidcIssuer = readString("FITNESSE_OIDC_ISSUER", null);
    String oidcClientId = readString("FITNESSE_OIDC_CLIENT_ID", null);
    String oidcAudience = readString("FITNESSE_OIDC_AUDIENCE", null);
    return new VertxConfig(port, rootPath, rootDirectory, authEnabled,
      mcpWebSocketEnabled, mcpGrpcEnabled, mcpGrpcPort, requestTimeoutMillis, idleTimeoutSeconds,
      testPoolSize, testMaxQueue, oidcEnabled, oidcIssuer, oidcClientId, oidcAudience);
  }

  public int port() {
    return port;
  }

  public static VertxConfig fromJson(io.vertx.core.json.JsonObject json, VertxConfig fallback) {
    if (json == null) {
      return fallback;
    }
    int port = json.getInteger("port", fallback.port());
    String rootPath = json.getString("rootPath", fallback.rootPath());
    String rootDirectory = json.getString("rootDirectory", fallback.rootDirectory());
    boolean authEnabled = json.getBoolean("authEnabled", fallback.authEnabled());
    boolean mcpWebSocketEnabled = json.getBoolean("mcpWebSocketEnabled", fallback.mcpWebSocketEnabled());
    boolean mcpGrpcEnabled = json.getBoolean("mcpGrpcEnabled", fallback.mcpGrpcEnabled());
    int mcpGrpcPort = json.getInteger("mcpGrpcPort", fallback.mcpGrpcPort());
    int requestTimeoutMillis = json.getInteger("requestTimeoutMillis", fallback.requestTimeoutMillis());
    int idleTimeoutSeconds = json.getInteger("idleTimeoutSeconds", fallback.idleTimeoutSeconds());
    int testPoolSize = json.getInteger("testPoolSize", fallback.testPoolSize());
    int testMaxQueue = json.getInteger("testMaxQueue", fallback.testMaxQueue());
    boolean oidcEnabled = json.getBoolean("oidcEnabled", fallback.oidcEnabled());
    String oidcIssuer = json.getString("oidcIssuer", fallback.oidcIssuer());
    String oidcClientId = json.getString("oidcClientId", fallback.oidcClientId());
    String oidcAudience = json.getString("oidcAudience", fallback.oidcAudience());
    return new VertxConfig(port, rootPath, rootDirectory, authEnabled, mcpWebSocketEnabled, mcpGrpcEnabled,
      mcpGrpcPort, requestTimeoutMillis, idleTimeoutSeconds, testPoolSize, testMaxQueue,
      oidcEnabled, oidcIssuer, oidcClientId, oidcAudience);
  }

  public static VertxConfig fromContext(fitnesse.FitNesseContext context) {
    int requestTimeoutMillis = readInt("FITNESSE_HTTP_TIMEOUT_MS", 30_000);
    int idleTimeoutSeconds = readInt("FITNESSE_IDLE_TIMEOUT_SEC", 60);
    int cores = Runtime.getRuntime().availableProcessors();
    int defaultPoolSize = Math.max(2, cores);
    int testPoolSize = readInt("FITNESSE_TEST_POOL_SIZE", defaultPoolSize);
    int defaultQueue = testPoolSize * 4;
    int testMaxQueue = readInt("FITNESSE_TEST_MAX_QUEUE", defaultQueue);
    boolean authEnabled = !(context.authenticator instanceof fitnesse.authentication.PromiscuousAuthenticator);
    boolean mcpWebSocketEnabled = readBoolean("FITNESSE_MCP_WS_ENABLED", false);
    boolean mcpGrpcEnabled = readBoolean("FITNESSE_MCP_GRPC_ENABLED", false);
    int mcpGrpcPort = readInt("FITNESSE_MCP_GRPC_PORT", 50051);
    boolean oidcEnabled = readBoolean("FITNESSE_OIDC_ENABLED", false);
    String oidcIssuer = readString("FITNESSE_OIDC_ISSUER", null);
    String oidcClientId = readString("FITNESSE_OIDC_CLIENT_ID", null);
    String oidcAudience = readString("FITNESSE_OIDC_AUDIENCE", null);
    return new VertxConfig(
      context.port,
      context.rootPath,
      context.getRootDirectoryName(),
      authEnabled,
      mcpWebSocketEnabled,
      mcpGrpcEnabled,
      mcpGrpcPort,
      requestTimeoutMillis,
      idleTimeoutSeconds,
      testPoolSize,
      testMaxQueue,
      oidcEnabled,
      oidcIssuer,
      oidcClientId,
      oidcAudience
    );
  }

  public String rootPath() {
    return rootPath;
  }

  public String rootDirectory() {
    return rootDirectory;
  }

  public boolean authEnabled() {
    return authEnabled;
  }

  public boolean mcpWebSocketEnabled() {
    return mcpWebSocketEnabled;
  }

  public boolean mcpGrpcEnabled() {
    return mcpGrpcEnabled;
  }

  public int mcpGrpcPort() {
    return mcpGrpcPort;
  }

  public int requestTimeoutMillis() {
    return requestTimeoutMillis;
  }

  public int idleTimeoutSeconds() {
    return idleTimeoutSeconds;
  }

  public int testPoolSize() {
    return testPoolSize;
  }

  public int testMaxQueue() {
    return testMaxQueue;
  }

  public boolean oidcEnabled() {
    return oidcEnabled;
  }

  public String oidcIssuer() {
    return oidcIssuer;
  }

  public String oidcClientId() {
    return oidcClientId;
  }

  public String oidcAudience() {
    return oidcAudience;
  }

  private static int readInt(String key, int fallback) {
    String raw = readString(key, null);
    if (raw == null || raw.isEmpty()) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static boolean readBoolean(String key, boolean fallback) {
    String raw = readString(key, null);
    if (raw == null || raw.isEmpty()) {
      return fallback;
    }
    return Boolean.parseBoolean(raw);
  }

  private static String readString(String key, String fallback) {
    String value = System.getProperty(key);
    if (value == null || value.isEmpty()) {
      value = System.getenv(key);
    }
    return (value == null || value.isEmpty()) ? fallback : value;
  }
}
