package fitnesse.vertx;

import io.vertx.core.file.FileSystem;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves access policy using a master policy file plus optional overrides
 * found in nested wiki folders (closest policy wins unless deny is non-overridable).
 */
final class AccessPolicyResolver {
  private final Path root;
  private final FileSystem fileSystem;
  private final AccessPolicy master;
  private final Map<Path, AccessPolicy> cache = new ConcurrentHashMap<>();

  AccessPolicyResolver(Path root, FileSystem fileSystem) {
    this.root = root;
    this.fileSystem = fileSystem;
    this.master = AccessPolicy.load(root, fileSystem);
  }

  AccessPolicy.Decision decide(String wikiPath, AccessPolicy.Surface surface) {
    String normalized = normalize(wikiPath);
    AccessPolicy.PolicyEntry effective = master.entryFor(normalized);
    if (!normalized.isEmpty()) {
      String[] segments = normalized.split("/");
      Path current = root;
      for (int i = 0; i < segments.length; i++) {
        if (segments[i].isEmpty()) {
          continue;
        }
        current = current.resolve(segments[i]);
        AccessPolicy local = loadPolicy(current);
        if (local != null) {
          String relative = join(segments, i + 1);
          effective = effective.merge(local.entryFor(relative));
        }
      }
    }
    return effective.decision(surface);
  }

  private AccessPolicy loadPolicy(Path folder) {
    return cache.computeIfAbsent(folder, path -> {
      Path policyPath = path.resolve(".fitnesse").resolve("policy.json");
      if (!fileSystem.existsBlocking(policyPath.toString())) {
        return null;
      }
      return AccessPolicy.load(path, fileSystem);
    });
  }

  private static String normalize(String wikiPath) {
    if (wikiPath == null) {
      return "";
    }
    String normalized = wikiPath.startsWith("/") ? wikiPath.substring(1) : wikiPath;
    return normalized.replace('\\', '/');
  }

  private static String join(String[] segments, int startIndex) {
    if (segments == null || startIndex >= segments.length) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (int i = startIndex; i < segments.length; i++) {
      if (segments[i] == null || segments[i].isEmpty()) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append('/');
      }
      builder.append(segments[i]);
    }
    return builder.toString();
  }
}
