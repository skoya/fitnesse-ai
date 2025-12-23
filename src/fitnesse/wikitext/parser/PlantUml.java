package fitnesse.wikitext.parser;

import fitnesse.wikitext.diagram.PlantUmlRenderer;

public class PlantUml extends SymbolType implements Rule, Translation {
  public static final PlantUml symbolType = new PlantUml();

  public PlantUml() {
    super("PlantUml");
    wikiMatcher(new Matcher().startLine().string("!plantuml"));
    wikiRule(this);
    htmlTranslation(this);
  }

  @Override
  public Maybe<Symbol> parse(Symbol current, Parser parser) {
    String literal = parser.parseLiteral(SymbolType.ClosePlantUml);
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
    return PlantUmlRenderer.render(source);
  }
}
