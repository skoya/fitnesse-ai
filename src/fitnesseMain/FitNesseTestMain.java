package fitnesseMain;

import fitnesse.junit.FitNesseCliRunner;
import fitnesse.testsystems.TestSummary;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;

public final class FitNesseTestMain {
  public static void main(String[] args) {
    Map<String, String> params = parseArgs(args);
    if (!params.containsKey("root") || !params.containsKey("suite")) {
      System.err.println("Usage: --root <FitNesseRoot> --suite <SuitePage> [--out <dir>] [--format junit,html,json] [--port <port>] [--suiteFilter <tags>] [--excludeSuiteFilter <tags>] [--debug true|false]");
      System.exit(2);
    }

    String root = params.get("root");
    String suite = params.get("suite");
    String out = params.getOrDefault("out", defaultOutputDir());
    String suiteFilter = params.get("suiteFilter");
    String excludeSuiteFilter = params.get("excludeSuiteFilter");
    int port = parseInt(params.get("port"), 0);
    boolean debug = Boolean.parseBoolean(params.getOrDefault("debug", "true"));
    Set<String> formats = parseFormats(params.getOrDefault("format", "junit,html,json"));

    Path outputRoot = Path.of(out);
    Path htmlOutput = outputRoot.resolve("html");
    File junitOutput = outputRoot.resolve("junit").toFile();

    Vertx vertx = Vertx.vertx();
    FileSystem fs = vertx.fileSystem();
    try {
      fs.mkdirsBlocking(outputRoot.toString());
      if (formats.contains("html")) {
        fs.mkdirsBlocking(htmlOutput.toString());
      }
      if (formats.contains("junit")) {
        fs.mkdirsBlocking(junitOutput.toPath().toString());
      }

      FitNesseCliRunner.RunRequest request = new FitNesseCliRunner.RunRequest(
        root,
        suite,
        suiteFilter,
        excludeSuiteFilter,
        port,
        debug,
        formats.contains("html"),
        formats.contains("junit"),
        htmlOutput.toString(),
        junitOutput
      );

      FitNesseCliRunner.RunResult result = FitNesseCliRunner.run(request);
      writeSummaryJson(fs, outputRoot.resolve("summary.json"), result.summary, formats.contains("json"));
      System.exit(exitCode(result.summary));
    } catch (Exception e) {
      e.printStackTrace(System.err);
      System.exit(1);
    } finally {
      vertx.close();
    }
  }

  private static void writeSummaryJson(FileSystem fs, Path target, TestSummary summary, boolean enabled) {
    if (!enabled) {
      return;
    }
    int right = summary.getRight();
    int wrong = summary.getWrong();
    int ignored = summary.getIgnores();
    int exceptions = summary.getExceptions();
    int total = right + wrong + ignored + exceptions;
    String json = "{\n"
      + "  \"right\": " + right + ",\n"
      + "  \"wrong\": " + wrong + ",\n"
      + "  \"ignored\": " + ignored + ",\n"
      + "  \"exceptions\": " + exceptions + ",\n"
      + "  \"total\": " + total + "\n"
      + "}\n";
    fs.writeFileBlocking(target.toString(), io.vertx.core.buffer.Buffer.buffer(json, StandardCharsets.UTF_8.name()));
  }

  private static int exitCode(TestSummary summary) {
    if (summary.getWrong() > 0 || summary.getExceptions() > 0) {
      return 1;
    }
    return 0;
  }

  private static String defaultOutputDir() {
    return Path.of("build", "test-results", "fitnesse-cli").toString();
  }

  private static Set<String> parseFormats(String raw) {
    Set<String> formats = new TreeSet<>();
    if (raw == null || raw.isEmpty()) {
      return formats;
    }
    for (String part : raw.split(",")) {
      String normalized = part.trim().toLowerCase();
      if (!normalized.isEmpty()) {
        formats.add(normalized);
      }
    }
    return formats;
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

  private static int parseInt(String value, int fallback) {
    if (value == null || value.isEmpty()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
