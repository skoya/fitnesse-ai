package fitnesse.wikitext.diagram;

import fitnesse.html.HtmlUtil;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class PlantUmlRenderer {
  private static final String SERVER_PROPERTY = "fitnesse.plantuml.server";
  private static final String FORMAT_PROPERTY = "fitnesse.plantuml.format";
  private static final String STYLE_PROPERTY = "fitnesse.plantuml.style";
  private static final String STYLE_RESOURCE = "fitnesse/resources/plantuml/fitnesse-style.puml";

  private PlantUmlRenderer() {
  }

  public static String render(String source) {
    String normalized = normalize(source);
    String withHeader = ensureStartEnd(normalized);
    String styled = applyStyle(withHeader);
    String server = resolveServer();
    if (server.isEmpty()) {
      String inlineSvg = renderInlineSvg(styled);
      if (!inlineSvg.isEmpty()) {
        return "<div class=\"fitnesse-diagram plantuml plantuml-inline\">" + inlineSvg + "</div>";
      }
      return "<pre class=\"plantuml-source\">" + HtmlUtil.escapeHTML(styled) + "</pre>";
    }
    String format = resolveFormat();
    String encoded = PlantUmlEncoder.encode(styled);
    String url = trimTrailingSlash(server) + "/" + format + "/" + encoded;
    return "<div class=\"fitnesse-diagram plantuml\">" +
      "<img class=\"plantuml-image\" alt=\"PlantUML diagram\" src=\"" + HtmlUtil.escapeHTML(url) + "\"/>" +
      "</div>";
  }

  private static String normalize(String source) {
    String value = source;
    if (value.startsWith("\r\n")) {
      value = value.substring(2);
    } else if (value.startsWith("\n")) {
      value = value.substring(1);
    }
    if (!value.endsWith("\n")) {
      value += "\n";
    }
    return value;
  }

  private static String ensureStartEnd(String source) {
    String trimmed = source.trim();
    boolean hasStart = trimmed.toLowerCase().startsWith("@startuml");
    boolean hasEnd = trimmed.toLowerCase().endsWith("@enduml");
    if (hasStart && hasEnd) {
      return source;
    }
    StringBuilder builder = new StringBuilder();
    builder.append("@startuml\n");
    builder.append(source.trim()).append("\n");
    builder.append("@enduml\n");
    return builder.toString();
  }

  private static String resolveServer() {
    String server = System.getProperty(SERVER_PROPERTY);
    if (server == null || server.isBlank()) {
      server = System.getenv("FITNESSE_PLANTUML_SERVER");
    }
    if (server == null) {
      return "";
    }
    String trimmed = server.trim();
    if (trimmed.equalsIgnoreCase("local") || trimmed.equalsIgnoreCase("inline")) {
      return "";
    }
    return trimmed;
  }

  private static String resolveFormat() {
    String format = System.getProperty(FORMAT_PROPERTY);
    if (format == null || format.isBlank()) {
      return "svg";
    }
    return format.trim();
  }

  private static String applyStyle(String source) {
    String styleSetting = System.getProperty(STYLE_PROPERTY);
    if (styleSetting != null && styleSetting.trim().equalsIgnoreCase("none")) {
      return source;
    }
    String style = loadStyle();
    if (style.isEmpty()) {
      return source;
    }
    if (!style.endsWith("\n")) {
      style += "\n";
    }
    return insertAfterStartUml(source, style);
  }

  private static String insertAfterStartUml(String source, String style) {
    String[] lines = source.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String trimmed = lines[i].trim();
      if (trimmed.equalsIgnoreCase("@startuml") || trimmed.startsWith("@startuml ")) {
        StringBuilder result = new StringBuilder();
        for (int j = 0; j <= i; j++) {
          result.append(lines[j]).append("\n");
        }
        result.append(style);
        for (int j = i + 1; j < lines.length; j++) {
          result.append(lines[j]);
          if (j < lines.length - 1) {
            result.append("\n");
          }
        }
        return result.toString();
      }
    }
    return style + source;
  }

  private static String loadStyle() {
    try (java.io.InputStream input = PlantUmlRenderer.class.getClassLoader().getResourceAsStream(STYLE_RESOURCE)) {
      if (input == null) {
        return "";
      }
      return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
    } catch (Exception e) {
      return "";
    }
  }

  private static String trimTrailingSlash(String value) {
    if (value.endsWith("/")) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }

  private static String renderInlineSvg(String source) {
    try {
      SourceStringReader reader = new SourceStringReader(source);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      if (reader.outputImage(output, new FileFormatOption(FileFormat.SVG)) == null) {
        return "";
      }
      return output.toString(StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "";
    }
  }

}
