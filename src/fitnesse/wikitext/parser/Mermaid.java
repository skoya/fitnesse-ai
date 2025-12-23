package fitnesse.wikitext.parser;

import fitnesse.html.HtmlUtil;

public class Mermaid extends SymbolType implements Rule, Translation {
  public static final Mermaid symbolType = new Mermaid();

  public Mermaid() {
    super("Mermaid");
    wikiMatcher(new Matcher().startLine().string("!mermaid"));
    wikiRule(this);
    htmlTranslation(this);
  }

  @Override
  public Maybe<Symbol> parse(Symbol current, Parser parser) {
    String literal = parser.parseLiteral(SymbolType.CloseMermaid);
    if (parser.atEnd()) {
      return new Maybe<>(Symbol.listOf(current.asText(), new Symbol(SymbolType.Text, literal)));
    }
    if (parser.peek().isType(SymbolType.Newline)) {
      parser.moveNext(1);
    }
    return new Maybe<>(current.add(literal));
  }

  @Override
  public String toTarget(Translator translator, Symbol symbol) {
    String source = symbol.childAt(0).getContent();
    String escaped = HtmlUtil.escapeHTML(normalize(source));
    return "<div class=\"fitnesse-diagram mermaid\"><pre class=\"mermaid\">" + escaped + "</pre></div>";
  }

  private String normalize(String value) {
    if (value.startsWith("\r\n")) {
      return value.substring(2);
    }
    if (value.startsWith("\n")) {
      return value.substring(1);
    }
    return value;
  }
}
