package fitnesse.mcp;

import fitnesse.authentication.Authenticator;
import io.vertx.ext.web.RoutingContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Enforces basic authentication for MCP endpoints when enabled.
 */
public final class McpAccessController {
  private final Authenticator authenticator;
  private final boolean authEnabled;

  public McpAccessController(Authenticator authenticator, boolean authEnabled) {
    this.authenticator = authenticator;
    this.authEnabled = authEnabled;
  }

  public boolean authorize(RoutingContext ctx) {
    if (!authEnabled) {
      return true;
    }
    if (ctx.user() != null || ctx.get("fitnesse.username") != null) {
      return true;
    }
    Credentials creds = parseBasicAuth(ctx);
    if (creds == null || authenticator == null || !authenticator.isAuthenticated(creds.username, creds.password)) {
      ctx.response()
        .setStatusCode(401)
        .putHeader("WWW-Authenticate", "Basic realm=\"FitNesse\"")
        .end("Unauthorized");
      return false;
    }
    return true;
  }

  public String resolveActor(RoutingContext ctx) {
    Object user = ctx.get("fitnesse.username");
    if (user != null) {
      return String.valueOf(user);
    }
    Credentials creds = parseBasicAuth(ctx);
    if (creds != null && creds.username != null && !creds.username.isEmpty()) {
      return creds.username;
    }
    if (ctx.user() != null && ctx.user().principal() != null) {
      String name = ctx.user().principal().getString("preferred_username");
      if (name == null || name.isEmpty()) {
        name = ctx.user().principal().getString("upn");
      }
      if (name == null || name.isEmpty()) {
        name = ctx.user().principal().getString("sub");
      }
      if (name != null && !name.isEmpty()) {
        return name;
      }
    }
    return "anonymous";
  }

  private Credentials parseBasicAuth(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Basic ")) {
      return null;
    }
    String decoded;
    try {
      decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
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
