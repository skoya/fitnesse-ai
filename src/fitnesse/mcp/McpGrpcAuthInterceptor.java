package fitnesse.mcp;

import fitnesse.authentication.Authenticator;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Enforces basic authentication for MCP gRPC requests when enabled.
 */
public final class McpGrpcAuthInterceptor implements ServerInterceptor {
  static final Context.Key<String> ACTOR_KEY = Context.key("mcpActor");

  private static final Metadata.Key<String> AUTHORIZATION =
    Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final boolean authEnabled;
  private final Authenticator authenticator;

  public McpGrpcAuthInterceptor(boolean authEnabled, Authenticator authenticator) {
    this.authEnabled = authEnabled;
    this.authenticator = authenticator;
  }

  public String currentActor() {
    return ACTOR_KEY.get();
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                               ServerCallHandler<ReqT, RespT> next) {
    if (!authEnabled) {
      Context context = Context.current().withValue(ACTOR_KEY, "anonymous");
      return Contexts.interceptCall(context, call, headers, next);
    }

    Credentials creds = parseBasicAuth(headers.get(AUTHORIZATION));
    if (creds == null || authenticator == null || !authenticator.isAuthenticated(creds.username, creds.password)) {
      call.close(Status.UNAUTHENTICATED.withDescription("Unauthorized"), new Metadata());
      return new ServerCall.Listener<ReqT>() {
      };
    }

    Context context = Context.current().withValue(ACTOR_KEY, creds.username);
    return Contexts.interceptCall(context, call, headers, next);
  }

  private Credentials parseBasicAuth(String header) {
    if (header == null || !header.startsWith("Basic ")) {
      return null;
    }
    String decoded;
    try {
      decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      return null;
    }
    int split = decoded.indexOf(':');
    if (split <= 0) {
      return null;
    }
    return new Credentials(decoded.substring(0, split), decoded.substring(split + 1));
  }

  private static final class Credentials {
    private final String username;
    private final String password;

    private Credentials(String username, String password) {
      this.username = username;
      this.password = password;
    }
  }
}
