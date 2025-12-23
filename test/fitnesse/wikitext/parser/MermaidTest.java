package fitnesse.wikitext.parser;

import fitnesse.html.HtmlUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MermaidTest {
  @Test
  public void rendersMermaidBlock() {
    String input = "!mermaid\n" +
      "graph TD;\n" +
      "A-->B;\n" +
      "!endmermaid";
    String html = ParserTestHelper.translateTo(input);
    assertTrue(html.contains("fitnesse-diagram mermaid"));
    int start = html.indexOf("<pre class=\"mermaid\">");
    int end = html.indexOf("</pre>");
    assertTrue(start >= 0);
    assertTrue(end > start);
    String body = html.substring(start + "<pre class=\"mermaid\">".length(), end);
    assertEquals("graph TD;\nA-->B;\n", HtmlUtil.unescapeHTML(body));
  }
}
