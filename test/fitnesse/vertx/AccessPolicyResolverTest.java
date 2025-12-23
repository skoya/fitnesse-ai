package fitnesse.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccessPolicyResolverTest {

  @TempDir
  Path tempDir;

  @Test
  public void masterDenyBlocksLocalOverride() throws Exception {
    Vertx vertx = Vertx.vertx();
    Path root = Files.createDirectory(tempDir.resolve("root"));
    writePolicy(root, new JsonObject()
      .put("default", new JsonObject()
        .put("ui", "deny")
        .put("allowOverride", false)));
    Path team = Files.createDirectories(root.resolve("Team"));
    writePolicy(team, new JsonObject()
      .put("default", new JsonObject().put("ui", "allow")));

    AccessPolicyResolver resolver = new AccessPolicyResolver(root, vertx.fileSystem());
    assertEquals(AccessPolicy.Decision.DENY, resolver.decide("Team/Page", AccessPolicy.Surface.UI));
    vertx.close();
  }

  @Test
  public void allowOverridePermitsLocalPolicy() throws Exception {
    Vertx vertx = Vertx.vertx();
    Path root = Files.createDirectory(tempDir.resolve("root2"));
    writePolicy(root, new JsonObject()
      .put("default", new JsonObject()
        .put("ui", "deny")
        .put("allowOverride", true)));
    Path team = Files.createDirectories(root.resolve("Team"));
    writePolicy(team, new JsonObject()
      .put("default", new JsonObject().put("ui", "allow")));

    AccessPolicyResolver resolver = new AccessPolicyResolver(root, vertx.fileSystem());
    assertEquals(AccessPolicy.Decision.ALLOW, resolver.decide("Team/Page", AccessPolicy.Surface.UI));
    vertx.close();
  }

  private static void writePolicy(Path root, JsonObject policy) throws Exception {
    Path dir = Files.createDirectories(root.resolve(".fitnesse"));
    Files.writeString(dir.resolve("policy.json"), policy.encodePrettily(), StandardCharsets.UTF_8);
  }
}
