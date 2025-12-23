package fitnesse.vertx;

import fitnesse.testsystems.Assertion;
import fitnesse.testsystems.ExceptionResult;
import fitnesse.testsystems.ExecutionLogListener;
import fitnesse.testsystems.ExecutionLogListener.ExecutionContext;
import fitnesse.testsystems.TestPage;
import fitnesse.testsystems.TestResult;
import fitnesse.testsystems.TestSummary;
import fitnesse.testsystems.TestSystem;
import fitnesse.testsystems.TestSystemListener;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class RunMonitorLogListener implements TestSystemListener, ExecutionLogListener {
  private final RunMonitor monitor;

  public RunMonitorLogListener(RunMonitor monitor) {
    this.monitor = monitor;
  }

  @Override
  public void testSystemStarted(TestSystem testSystem) {
    monitor.log("info", "Test system started", null, testSystem.getName());
  }

  @Override
  public void testOutputChunk(TestPage testPage, String output) {
    monitor.log("debug", output, testPage.getFullPath(), null);
  }

  @Override
  public void testStarted(TestPage testPage) {
    monitor.log("info", "Test started", testPage.getFullPath(), null);
  }

  @Override
  public void testComplete(TestPage testPage, TestSummary testSummary) {
    String summary = String.format("R:%d W:%d I:%d E:%d",
      testSummary.getRight(), testSummary.getWrong(),
      testSummary.getIgnores(), testSummary.getExceptions());
    monitor.log("info", "Test complete (" + summary + ")", testPage.getFullPath(), null);
  }

  @Override
  public void testSystemStopped(TestSystem testSystem, Throwable cause) {
    if (cause == null) {
      monitor.log("info", "Test system stopped", null, testSystem.getName());
    } else {
      monitor.log("error", "Test system stopped: " + cause.getMessage(), null, testSystem.getName());
    }
  }

  @Override
  public void testAssertionVerified(Assertion assertion, TestResult testResult) {
  }

  @Override
  public void testExceptionOccurred(Assertion assertion, ExceptionResult exceptionResult) {
    monitor.log("error", exceptionResult.toString(), null, null);
  }

  @Override
  public void commandStarted(ExecutionContext context) {
    monitor.log("info", "Command started: " + context.getCommand(), null, context.getTestSystemName());
  }

  @Override
  public void stdOut(String output) {
    monitor.log("stdout", output, null, null);
  }

  @Override
  public void stdErr(String output) {
    monitor.log("stderr", output, null, null);
  }

  @Override
  public void exitCode(int exitCode) {
    monitor.log("info", "Process exit code " + exitCode, null, null);
  }

  @Override
  public void exceptionOccurred(Throwable e) {
    monitor.log("error", formatException(e), null, null);
  }

  private static String formatException(Throwable e) {
    if (e == null) {
      return "Unknown exception";
    }
    StringWriter writer = new StringWriter();
    e.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }
}
