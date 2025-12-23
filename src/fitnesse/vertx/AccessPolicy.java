package fitnesse.vertx;

import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import io.vertx.core.file.FileSystem;
import io.vertx.core.buffer.Buffer;

/**
 * Simple shared policy for UI/API/MCP surfaces with optional path overrides.
 * Decisions: allow, deny, or require authentication.
 */
final class AccessPolicy {
  enum Decision {ALLOW, DENY, AUTH_REQUIRED}
  enum Surface {UI, API, MCP}

  private final PolicyEntry defaults;
  private final Map<String, PolicyEntry> overrides; // key: path prefix

  private AccessPolicy(PolicyEntry defaults, Map<String, PolicyEntry> overrides) {
    this.defaults = defaults;
    this.overrides = overrides;
  }

  static AccessPolicy allowAll() {
    return new AccessPolicy(new PolicyEntry(Decision.ALLOW, Decision.ALLOW, Decision.ALLOW, false), Map.of());
  }

  static AccessPolicy load(Path root, FileSystem fs) {
    Path policyPath = root.resolve(".fitnesse").resolve("policy.json");
    if (!fs.existsBlocking(policyPath.toString())) {
      return allowAll();
    }
    try {
      Buffer buffer = fs.readFileBlocking(policyPath.toString());
      String raw = buffer.toString(StandardCharsets.UTF_8);
      JsonObject json = new JsonObject(raw);
      PolicyEntry defaults = PolicyEntry.fromJson(json.getJsonObject("default"),
        new PolicyEntry(Decision.ALLOW, Decision.ALLOW, Decision.ALLOW, false));
      Map<String, PolicyEntry> overrides = new HashMap<>();
      JsonObject over = json.getJsonObject("overrides", new JsonObject());
      for (String key : over.fieldNames()) {
        overrides.put(key, PolicyEntry.fromJson(over.getJsonObject(key), defaults));
      }
      return new AccessPolicy(defaults, overrides);
    } catch (RuntimeException e) {
      return allowAll();
    }
  }

  PolicyEntry entryFor(String path) {
    PolicyEntry entry = defaults;
    String bestMatch = "";
    String safePath = path == null ? "" : path;
    for (Map.Entry<String, PolicyEntry> e : overrides.entrySet()) {
      String prefix = e.getKey();
      if (safePath.startsWith(prefix) && prefix.length() > bestMatch.length()) {
        bestMatch = prefix;
        entry = e.getValue();
      }
    }
    return entry;
  }

  Decision decide(String path, Surface surface) {
    return entryFor(path).decision(surface);
  }

  static final class PolicyEntry {
    private final Decision ui;
    private final Decision api;
    private final Decision mcp;
    private final boolean allowOverride;

    private PolicyEntry(Decision ui, Decision api, Decision mcp, boolean allowOverride) {
      this.ui = ui;
      this.api = api;
      this.mcp = mcp;
      this.allowOverride = allowOverride;
    }

    Decision decision(Surface surface) {
      return switch (surface) {
        case API -> api;
        case MCP -> mcp;
        default -> ui;
      };
    }

    static PolicyEntry fromJson(JsonObject json, PolicyEntry fallback) {
      if (json == null) {
        return fallback;
      }
      return new PolicyEntry(
        decision(json.getString("ui"), fallback.ui),
        decision(json.getString("api"), fallback.api),
        decision(json.getString("mcp"), fallback.mcp),
        json.getBoolean("allowOverride", fallback.allowOverride)
      );
    }

    private static Decision decision(String raw, Decision fallback) {
      if (raw == null) {
        return fallback;
      }
      return switch (raw.toLowerCase()) {
        case "deny" -> Decision.DENY;
        case "auth", "auth_required", "require_auth" -> Decision.AUTH_REQUIRED;
        default -> Decision.ALLOW;
      };
    }

    PolicyEntry merge(PolicyEntry override) {
      if (override == null) {
        return this;
      }
      Decision mergedUi = mergeDecision(ui, allowOverride, override.ui);
      Decision mergedApi = mergeDecision(api, allowOverride, override.api);
      Decision mergedMcp = mergeDecision(mcp, allowOverride, override.mcp);
      boolean mergedAllowOverride =
        (ui == Decision.DENY || api == Decision.DENY || mcp == Decision.DENY) && !allowOverride
          ? allowOverride
          : override.allowOverride;
      return new PolicyEntry(mergedUi, mergedApi, mergedMcp, mergedAllowOverride);
    }

    private static Decision mergeDecision(Decision base, boolean baseAllowOverride, Decision override) {
      if (base == Decision.DENY && !baseAllowOverride) {
        return base;
      }
      return override;
    }
  }
}
