package fitnesse.wikitext.parser;

import fitnesse.wikitext.diagram.PlantUmlEncoder;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PlantUmlTest {
  @Test
  public void rendersPlantUmlBlock() {
    String originalServer = System.getProperty("fitnesse.plantuml.server");
    String originalFormat = System.getProperty("fitnesse.plantuml.format");
    try {
      System.setProperty("fitnesse.plantuml.server", "http://plantuml.test");
      System.setProperty("fitnesse.plantuml.format", "svg");

      String diagram = "Alice -> Bob: Hi";
      String input = "!plantuml\n" + diagram + "\n!endplantuml";
      String html = ParserTestHelper.translateTo(input);
      String encoded = PlantUmlEncoder.encode(styledSource(ensureStartEnd(normalize(diagram))));
      assertTrue(html.contains("fitnesse-diagram plantuml"));
      assertTrue(html.contains("plantuml.test/svg/" + encoded));
    } finally {
      if (originalServer == null) {
        System.clearProperty("fitnesse.plantuml.server");
      } else {
        System.setProperty("fitnesse.plantuml.server", originalServer);
      }
      if (originalFormat == null) {
        System.clearProperty("fitnesse.plantuml.format");
      } else {
        System.setProperty("fitnesse.plantuml.format", originalFormat);
      }
    }
  }

  @Test
  public void appliesDefaultStyleWhenRenderingInline() {
    String originalServer = System.getProperty("fitnesse.plantuml.server");
    try {
      System.setProperty("fitnesse.plantuml.server", "local");
      String input = "!plantuml\nAlice -> Bob: Hi\n!endplantuml";
      String html = ParserTestHelper.translateTo(input);
      assertTrue(html.contains("plantuml-inline"));
      assertTrue(html.contains("<svg"));
    } finally {
      if (originalServer == null) {
        System.clearProperty("fitnesse.plantuml.server");
      } else {
        System.setProperty("fitnesse.plantuml.server", originalServer);
      }
    }
  }

  private String normalize(String value) {
    String normalized = value;
    if (normalized.startsWith("\r\n")) {
      normalized = normalized.substring(2);
    } else if (normalized.startsWith("\n")) {
      normalized = normalized.substring(1);
    }
    if (!normalized.endsWith("\n")) {
      normalized += "\n";
    }
    return normalized;
  }

  private String ensureStartEnd(String source) {
    String trimmed = source.trim();
    boolean hasStart = trimmed.toLowerCase().startsWith("@startuml");
    boolean hasEnd = trimmed.toLowerCase().endsWith("@enduml");
    if (hasStart && hasEnd) {
      return source;
    }
    return "@startuml\n" + source.trim() + "\n@enduml\n";
  }

  private String styledSource(String source) {
    String style = loadStyle();
    if (style.isEmpty()) {
      return source;
    }
    if (!style.endsWith("\n")) {
      style += "\n";
    }
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

  private String loadStyle() {
    try (java.io.InputStream input =
           PlantUmlTest.class.getClassLoader()
             .getResourceAsStream("fitnesse/resources/plantuml/fitnesse-style.puml")) {
      if (input == null) {
        return "";
      }
      return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
    } catch (Exception e) {
      return "";
    }
  }
}
