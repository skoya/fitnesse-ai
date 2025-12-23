package fitnesse.junit;

import fitnesse.testsystems.TestPage;
import fitnesse.testsystems.TestSummary;
import fitnesse.testsystems.TestSystemListener;

import java.io.File;
import java.io.IOException;

public final class JUnitXmlTestSystemListener implements TestSystemListener {
  private final JUnitXMLTestResultRecorder recorder;

  public JUnitXmlTestSystemListener(File reportsDir) {
    this.recorder = new JUnitXMLTestResultRecorder(reportsDir);
  }

  @Override
  public void testOutputChunk(TestPage testPage, String output) {
  }

  @Override
  public void testComplete(TestPage testPage, TestSummary testSummary) {
    String testName = sanitize(testPage.getFullPath());
    int skipped = testSummary.getIgnores();
    int failures = testSummary.getWrong();
    int errors = testSummary.getExceptions();
    Throwable failure = null;
    if (failures > 0) {
      failure = new AssertionError("Wrong assertions: " + failures);
    } else if (errors > 0) {
      failure = new RuntimeException("Exceptions: " + errors);
    }
    try {
      recorder.recordTestResult(testName, skipped, failures, errors, failure, 0L);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String sanitize(String name) {
    if (name == null) {
      return "unknown";
    }
    return name.replace('/', '.').replace('\\', '.');
  }
}
