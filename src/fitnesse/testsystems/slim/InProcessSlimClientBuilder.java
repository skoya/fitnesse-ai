package fitnesse.testsystems.slim;

import fitnesse.slim.JavaSlimFactory;
import fitnesse.slim.SlimServer;
import fitnesse.slim.SlimService;
import fitnesse.slim.fixtureInteraction.FixtureInteraction;
import fitnesse.testsystems.ClientBuilder;
import fitnesse.testsystems.Descriptor;

import static fitnesse.testsystems.slim.SlimClientBuilder.SLIM_FLAGS;

/**
 * In-process version, mainly for testing FitNesse itself.
 */
public class InProcessSlimClientBuilder extends ClientBuilder<SlimClient> {

  private ClassLoader classLoader;

  public InProcessSlimClientBuilder(Descriptor descriptor, ClassLoader classLoader) {
    super(descriptor);
    this.classLoader = classLoader;
  }

  @Override
  public SlimClient build() {
    String[] slimFlags = getSlimFlags();

    String interactionClassName = null;
    Integer timeout = null;
    boolean verbose = false;

    for (int i = 0; i < slimFlags.length; i++) {
      String flag = slimFlags[i];
      if ("-i".equals(flag) && i + 1 < slimFlags.length) {
        interactionClassName = slimFlags[++i];
      } else if ("-s".equals(flag) && i + 1 < slimFlags.length) {
        try {
          timeout = Integer.parseInt(slimFlags[++i]);
        } catch (NumberFormatException ignored) {
          // fall back to default timeout (null)
        }
      } else if ("-v".equals(flag)) {
        verbose = true;
      }
    }

    FixtureInteraction interaction = JavaSlimFactory.createInteraction(interactionClassName, classLoader);
    SlimServer slimServer = createSlimServer(interaction, timeout, verbose);
    return new InProcessSlimClient(getTestSystemName(), slimServer, getExecutionLogListener(), classLoader);
  }

  @Override
  protected String defaultTestRunner() {
    return "in-process";
  }

  protected SlimServer createSlimServer(FixtureInteraction interaction, Integer timeout, boolean verbose) {
    return JavaSlimFactory.createJavaSlimFactory(interaction, timeout, verbose).getSlimServer();
  }

  protected String[] getSlimFlags() {
    String slimFlags = getVariable("slim.flags");
    if (slimFlags == null) {
      slimFlags = getVariable(SLIM_FLAGS);
    }
    return slimFlags == null ? new String[] {} : parseCommandLine(slimFlags);
  }

}
