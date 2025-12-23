package fitnesse.vertx;

import fitnesse.authentication.Authenticator;
import fitnesse.docstore.GitIdentity;
import fitnesse.docstore.GitIdentityHolder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Basic auth middleware backed by FitNesse Authenticator.
 */
public final class VertxAuthHandler implements Handler<RoutingContext> {
  private final Authenticator authenticator;

  public VertxAuthHandler(Authenticator authenticator) {
    this.authenticator = authenticator;
  }

  @Override
  public void handle(RoutingContext ctx) {
    GitIdentityHolder.clear();
    if (authenticator == null) {
      ctx.response().setStatusCode(401).end("No authenticator configured");
      return;
    }
    Credentials creds = parse(ctx.request().getHeader("Authorization"));
    if (creds == null || !authenticator.isAuthenticated(creds.username, creds.password)) {
      ctx.response()
        .setStatusCode(401)
        .putHeader("WWW-Authenticate", "Basic realm=\"FitNesse\"")
        .end("Unauthorized");
      return;
    }
    try {
      ctx.put("fitnesse.username", creds.username);
      // Stamp Git authorship for this request. Email unknown in basic auth, leave null.
      GitIdentityHolder.set(new GitIdentity(creds.username, null));
      ctx.next();
    } finally {
      GitIdentityHolder.clear();
    }
  }

  private Credentials parse(String header) {
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
