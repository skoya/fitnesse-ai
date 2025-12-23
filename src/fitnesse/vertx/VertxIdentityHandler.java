package fitnesse.vertx;

import fitnesse.docstore.GitIdentity;
import fitnesse.docstore.GitIdentityHolder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Extracts user identity from the request (headers or vertx user principal)
 * and populates {@link GitIdentityHolder} for downstream git commits.
 */
final class VertxIdentityHandler implements Handler<RoutingContext> {

  @Override
  public void handle(RoutingContext ctx) {
    GitIdentityHolder.clear();
    GitIdentity identity = resolve(ctx);
    if (identity != null && !identity.isEmpty()) {
      GitIdentityHolder.set(identity);
    }
    ctx.addBodyEndHandler(v -> GitIdentityHolder.clear());
    ctx.next();
  }

  private GitIdentity resolve(RoutingContext ctx) {
    // Preferred: explicit headers set by upstream auth (OAuth/OIDC proxy, etc.).
    String name = firstNonBlank(
      ctx.request().getHeader("X-FitNesse-User"),
      ctx.request().getHeader("X-Remote-User"),
      ctx.request().getHeader("Remote-User"));
    String email = firstNonBlank(
      ctx.request().getHeader("X-FitNesse-Email"),
      ctx.request().getHeader("X-Remote-Email"));

    if (ctx.user() != null && ctx.user().principal() != null) {
      var principal = ctx.user().principal();
      if (name == null) {
        name = firstNonBlank(principal.getString("name"), principal.getString("preferred_username"), principal.getString("sub"));
      }
      if (email == null) {
        email = firstNonBlank(principal.getString("email"), principal.getString("upn"));
      }
    }
    if (name == null && email == null) {
      return null;
    }
    return new GitIdentity(name, email);
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }
}
