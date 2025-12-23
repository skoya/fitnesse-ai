package fitnesse.ai;

/**
 * Provides simple Java test scaffolding templates.
 */
public final class TestGenTemplates {
  private TestGenTemplates() {
  }

  public static String junitTestTemplate(String className, String fixtureName) {
    String fixture = fixtureName == null || fixtureName.isEmpty() ? "MyFixture" : fixtureName;
    return "package fitnesse.generated;\n\n"
      + "import org.junit.Test;\n\n"
      + "public class " + className + " {\n"
      + "  @Test\n"
      + "  public void runsFixture() {\n"
      + "    // TODO: wire fixture \"" + fixture + "\" with your FitNesse suite.\n"
      + "  }\n"
      + "}\n";
  }

  public static String fixtureTemplate(String fixtureName) {
    String fixture = fixtureName == null || fixtureName.isEmpty() ? "MyFixture" : fixtureName;
    return "package fitnesse.generated;\n\n"
      + "public class " + fixture + " {\n"
      + "  public int sum(int a, int b) {\n"
      + "    return a + b;\n"
      + "  }\n"
      + "}\n";
  }
}
