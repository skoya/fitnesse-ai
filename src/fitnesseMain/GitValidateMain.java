package fitnesseMain;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;

public final class GitValidateMain {
  public static void main(String[] args) {
    Map<String, String> params = parseArgs(args);
    if (!params.containsKey("root")) {
      System.err.println("Usage: --root <FitNesseRoot> [--strict true|false]");
      System.exit(2);
    }

    Path root = Paths.get(params.get("root")).toAbsolutePath();
    boolean strict = Boolean.parseBoolean(params.getOrDefault("strict", "false"));

    Vertx vertx = Vertx.vertx();
    FileSystem fs = vertx.fileSystem();
    try {
      ValidationReport report = validate(fs, root);
      System.out.println("Pages scanned: " + report.pages);
      System.out.println("Warnings: " + report.warnings.size());
      System.out.println("Errors: " + report.errors.size());
      for (String warning : report.warnings) {
        System.out.println("WARN: " + warning);
      }
      for (String error : report.errors) {
        System.out.println("ERROR: " + error);
      }
      if (!report.errors.isEmpty() || (strict && !report.warnings.isEmpty())) {
        System.exit(1);
      }
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      System.exit(1);
    } finally {
      vertx.close();
    }
  }

  private static ValidationReport validate(FileSystem fs, Path root) {
    ValidationReport report = new ValidationReport();
    walk(fs, root.toString(), root, report);
    return report;
  }

  private static void walk(FileSystem fs, String current, Path root, ValidationReport report) {
    Path currentPath = Paths.get(current);
    if (currentPath.getFileName() != null && "files".equals(currentPath.getFileName().toString())) {
      return;
    }
    Path content = currentPath.resolve("content.txt");
    Path properties = currentPath.resolve("properties.xml");
    boolean hasContent = fs.existsBlocking(content.toString());
    boolean hasProperties = fs.existsBlocking(properties.toString());
    if (hasContent || hasProperties) {
      report.pages++;
      if (!hasContent) {
        report.warnings.add("Missing content.txt: " + root.relativize(currentPath));
      }
      if (!hasProperties) {
        report.warnings.add("Missing properties.xml: " + root.relativize(currentPath));
      } else {
        validateXml(fs, properties, report, root);
      }
    }
    for (String entry : fs.readDirBlocking(current)) {
      if (fs.propsBlocking(entry).isDirectory()) {
        walk(fs, entry, root, report);
      }
    }
  }

  private static void validateXml(FileSystem fs, Path properties, ValidationReport report, Path root) {
    try {
      DocumentBuilderFactory factory = secureFactory();
      byte[] bytes = fs.readFileBlocking(properties.toString()).getBytes();
      Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
      if (doc == null) {
        report.errors.add("Unreadable properties.xml: " + root.relativize(properties));
      }
    } catch (Exception e) {
      report.errors.add("Invalid properties.xml: " + root.relativize(properties) + " (" + e.getMessage() + ")");
    }
  }

  private static DocumentBuilderFactory secureFactory() {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    } catch (Exception ignored) {
      // Fall back to defaults if features aren't supported.
    }
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory;
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

  private static final class ValidationReport {
    int pages;
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();
  }
}
