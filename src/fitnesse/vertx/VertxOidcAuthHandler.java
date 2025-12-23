package fitnesse.vertx;

import fitnesse.docstore.GitIdentity;
import fitnesse.docstore.GitIdentityHolder;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import io.vertx.ext.web.RoutingContext;

import java.util.logging.Logger;

/**
 * OIDC Bearer auth for UI/API/MCP routes. Validates JWT using issuer metadata and sets Git identity.
 */
public final class VertxOidcAuthHandler implements Handler<RoutingContext> {
  private static final Logger LOG = Logger.getLogger(VertxOidcAuthHandler.class.getName());

  private final OAuth2Auth auth;
  private final String audience;

  private VertxOidcAuthHandler(OAuth2Auth auth, String audience) {
    this.auth = auth;
    this.audience = audience;
  }

  public static VertxOidcAuthHandler create(Vertx vertx, VertxConfig config) {
    return create(vertx, config.oidcIssuer(), config.oidcClientId(), config.oidcAudience());
  }

  public static VertxOidcAuthHandler create(Vertx vertx, String issuer, String clientId, String audience) {
    OAuth2Options options = new OAuth2Options()
      .setSite(issuer)
      .setClientId(clientId);
    OAuth2Auth oauth2 = OpenIDConnectAuth.discover(vertx, options)
      .toCompletionStage()
      .toCompletableFuture()
      .join();
    return new VertxOidcAuthHandler(oauth2, audience);
  }

  @Override
  public void handle(RoutingContext ctx) {
    GitIdentityHolder.clear();
    String header = ctx.request().getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      ctx.response().setStatusCode(401).end("Missing bearer token");
      return;
    }
    String token = header.substring("Bearer ".length()).trim();
    auth.authenticate(new JsonObject().put("access_token", token))
      .onFailure(err -> {
        LOG.fine("OIDC auth failed: " + err.getMessage());
        ctx.response().setStatusCode(401).end("Unauthorized");
      })
      .onSuccess(user -> {
        if (!audienceAllowed(user)) {
          ctx.response().setStatusCode(403).end("Forbidden");
          return;
        }
        ctx.setUser(user);
        String username = preferredUsername(user);
        String email = email(user);
        GitIdentityHolder.set(new GitIdentity(username, email));
        ctx.put("fitnesse.username", username);
        try {
          ctx.next();
        } finally {
          GitIdentityHolder.clear();
        }
      });
  }

  private boolean audienceAllowed(User user) {
    if (audience == null || audience.isEmpty()) {
      return true;
    }
    JsonObject principal = user.principal();
    if (principal == null) {
      return false;
    }
    if (principal.containsKey("aud")) {
      Object aud = principal.getValue("aud");
      if (aud instanceof String) {
        return audience.equals(aud);
      }
      if (aud instanceof Iterable) {
        for (Object val : (Iterable<?>) aud) {
          if (audience.equals(String.valueOf(val))) {
            return true;
          }
        }
        return false;
      }
    }
    return true;
  }

  private String preferredUsername(User user) {
    JsonObject principal = user.principal();
    if (principal == null) {
      return "unknown";
    }
    String name = principal.getString("preferred_username");
    if (name == null || name.isEmpty()) {
      name = principal.getString("upn");
    }
    if (name == null || name.isEmpty()) {
      name = principal.getString("sub");
    }
    return name == null ? "unknown" : name;
  }

  private String email(User user) {
    JsonObject principal = user.principal();
    if (principal == null) {
      return null;
    }
    String email = principal.getString("email");
    return (email == null || email.isEmpty()) ? null : email;
  }
}
