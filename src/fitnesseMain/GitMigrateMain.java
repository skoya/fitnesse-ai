package fitnesseMain;

import fitnesse.docstore.GitRepository;
import io.vertx.core.Vertx;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileSystem;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GitMigrateMain {
  public static void main(String[] args) {
    Map<String, String> params = parseArgs(args);
    if (!params.containsKey("from") || !params.containsKey("to")) {
      System.err.println("Usage: --from <FitNesseRoot> --to <gitDir> [--initRepo true|false] [--dryRun true|false]");
      System.exit(2);
    }

    Path from = Paths.get(params.get("from")).toAbsolutePath();
    Path to = Paths.get(params.get("to")).toAbsolutePath();
    boolean initRepo = Boolean.parseBoolean(params.getOrDefault("initRepo", "true"));
    boolean dryRun = Boolean.parseBoolean(params.getOrDefault("dryRun", "false"));

    Vertx vertx = Vertx.vertx();
    try {
      if (!dryRun) {
        vertx.fileSystem().mkdirsBlocking(to.toString());
      }
      CopyStats stats = copyTree(vertx.fileSystem(), from, to, dryRun);

      if (!dryRun && initRepo) {
        GitRepository repo = new GitRepository(to);
        repo.init();
        repo.add(stats.copied);
        repo.commit("wiki: import FitNesseRoot");
      }

      System.out.println("Migrated pages: " + stats.fileCount);
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      System.exit(1);
    } finally {
      vertx.close();
    }
  }

  private static CopyStats copyTree(FileSystem fs, Path from, Path to, boolean dryRun) {
    CopyStats stats = new CopyStats();
    copyDir(fs, from.toString(), to.toString(), stats, dryRun);
    return stats;
  }

  private static void copyDir(FileSystem fs, String sourceDir, String targetDir, CopyStats stats, boolean dryRun) {
    if (!dryRun) {
      fs.mkdirsBlocking(targetDir);
    }
    for (String entry : fs.readDirBlocking(sourceDir)) {
      var props = fs.propsBlocking(entry);
      Path relative = Paths.get(sourceDir).relativize(Paths.get(entry));
      String dest = Paths.get(targetDir).resolve(relative.getFileName()).toString();
      if (props.isDirectory()) {
        copyDir(fs, entry, dest, stats, dryRun);
      } else {
        if (!dryRun) {
          fs.copyBlocking(entry, dest);
        }
        stats.fileCount++;
        stats.copied.add(Paths.get(dest));
      }
    }
  }

  private static Map<String, String> parseArgs(String[] args) {
    Map<String, String> params = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (!arg.startsWith("--")) {
        continue;
      }
      String key = arg.substring(2);
      String value = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "true";
      params.put(key, value);
    }
    return params;
  }

  private static final class CopyStats {
    int fileCount;
    List<Path> copied = new ArrayList<>();
  }
}
