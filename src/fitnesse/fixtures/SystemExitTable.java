package fitnesse.fixtures;

import fit.ColumnFixture;
import fitnesse.slim.SlimIgnoreScriptTestException;

public class SystemExitTable  extends ColumnFixture {

  private int exitCode;
  
  private Throwable exception;
  
  public void setSystemExitCode(int exitCode) {
    this.exitCode = exitCode;
  }

  // slim:
  @Override
  public void execute() throws Exception {
    if (shouldIgnoreOnModernJdk()) {
      throw new SlimIgnoreScriptTestException("System.exit prevention is unsupported on Java 21+");
    }
    try {
      System.exit(exitCode);
    } catch (Throwable e) { // NOSONAR
      exception = e;
    }
  }

  // fit:
  public boolean valid() throws Exception {
    exitSystem(exitCode);
    return true;
  }

  public String exceptionMessage() {
    return exception.getMessage();
  }
  
  public void exitSystem(int code) {
    System.exit(code);
  }

  private boolean shouldIgnoreOnModernJdk() {
    String prevent = System.getProperty("prevent.system.exit");
    if (!Boolean.parseBoolean(prevent)) {
      return false;
    }
    return detectJavaMajorVersion() >= 21;
  }

  private int detectJavaMajorVersion() {
    String version = System.getProperty("java.specification.version", "");
    if (version.startsWith("1.")) {
      version = version.substring(2);
    }
    int dot = version.indexOf('.');
    if (dot > 0) {
      version = version.substring(0, dot);
    }
    try {
      return Integer.parseInt(version);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
