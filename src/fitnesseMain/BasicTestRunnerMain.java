package fitnesseMain;

import fitnesse.junit.JUnitHelper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class BasicTestRunnerMain {
  public static void main(String[] args) {
    Map<String, String> params = parseArgs(args);
    if (!params.containsKey("root") || !params.containsKey("suite")) {
      System.err.println("Usage: --root <FitNesseRoot> --suite <SuitePage> [--out <dir>] [--port <port>] [--suiteFilter <tags>] [--excludeSuiteFilter <tags>]");
      System.exit(2);
    }

    String root = params.get("root");
    String suite = params.get("suite");
    String out = params.getOrDefault("out", defaultOutputDir());
    String suiteFilter = params.get("suiteFilter");
    String excludeSuiteFilter = params.get("excludeSuiteFilter");
    int port = parseInt(params.get("port"), 0);

    try {
      JUnitHelper helper = new JUnitHelper(root, out);
      if (port > 0) {
        helper.setPort(port);
      }
      if (suiteFilter != null || excludeSuiteFilter != null) {
        helper.assertSuitePasses(suite, suiteFilter, excludeSuiteFilter);
      } else {
        helper.assertSuitePasses(suite);
      }
      System.exit(0);
    } catch (AssertionError | Exception e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  private static String defaultOutputDir() {
    return new File("build/test-results/basic-runner").getPath();
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
