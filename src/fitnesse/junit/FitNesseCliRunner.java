package fitnesse.junit;

import fitnesse.ContextConfigurator;
import fitnesse.FitNesseContext;
import fitnesse.testrunner.MultipleTestsRunner;
import fitnesse.testrunner.SuiteContentsFinder;
import fitnesse.testrunner.run.TestRun;
import fitnesse.testsystems.ConsoleExecutionLogListener;
import fitnesse.testsystems.TestSummary;
import fitnesse.wiki.PageCrawler;
import fitnesse.wiki.PathParser;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPagePath;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class FitNesseCliRunner {
  private FitNesseCliRunner() {
  }

  public static RunResult run(RunRequest request) throws Exception {
    FitNesseContext context = ContextConfigurator
      .systemDefaults()
      .withRootPath(request.rootPath)
      .withPort(request.port)
      .makeFitNesseContext();

    JavaFormatter formatter = new JavaFormatter(request.suiteName);
    if (request.htmlEnabled) {
      formatter.setResultsRepository(new JavaFormatter.FolderResultsRepository(request.htmlOutputDir));
    } else {
      formatter.setResultsRepository(new NullResultsRepository());
    }

    List<WikiPage> pages = initChildren(request.suiteName, request.suiteFilter, request.excludeSuiteFilter, context);
    TestRun run = JUnitHelper.createTestRun(context, pages);
    MultipleTestsRunner testRunner = JUnitHelper.createTestRunner(run, context, request.debugMode);
    testRunner.addTestSystemListener(formatter);
    if (request.junitEnabled) {
      testRunner.addTestSystemListener(new JUnitXmlTestSystemListener(request.junitOutputDir));
    }
    testRunner.addExecutionLogListener(new ConsoleExecutionLogListener());

    testRunner.executeTestPages();
    TestSummary summary = formatter.getTotalSummary();
    return new RunResult(summary);
  }

  private static List<WikiPage> initChildren(String suiteName, String suiteFilter, String excludeSuiteFilter, FitNesseContext context) {
    WikiPage suiteRoot = getSuiteRootPage(suiteName, context);
    if (!suiteRoot.getData().hasAttribute("Suite")) {
      return Arrays.asList(suiteRoot);
    }
    return new SuiteContentsFinder(suiteRoot, new fitnesse.testrunner.SuiteFilter(suiteFilter, excludeSuiteFilter), context.getRootPage())
      .getAllPagesToRunForThisSuite();
  }

  private static WikiPage getSuiteRootPage(String suiteName, FitNesseContext context) {
    WikiPagePath path = PathParser.parse(suiteName);
    PageCrawler crawler = context.getRootPage(Collections.emptyMap()).getPageCrawler();
    return crawler.getPage(path);
  }

  public static final class RunRequest {
    final String rootPath;
    final String suiteName;
    final String suiteFilter;
    final String excludeSuiteFilter;
    final int port;
    final boolean debugMode;
    final boolean htmlEnabled;
    final boolean junitEnabled;
    final String htmlOutputDir;
    final java.io.File junitOutputDir;

    public RunRequest(String rootPath, String suiteName, String suiteFilter, String excludeSuiteFilter, int port,
                      boolean debugMode, boolean htmlEnabled, boolean junitEnabled,
                      String htmlOutputDir, java.io.File junitOutputDir) {
      this.rootPath = rootPath;
      this.suiteName = suiteName;
      this.suiteFilter = suiteFilter;
      this.excludeSuiteFilter = excludeSuiteFilter;
      this.port = port;
      this.debugMode = debugMode;
      this.htmlEnabled = htmlEnabled;
      this.junitEnabled = junitEnabled;
      this.htmlOutputDir = htmlOutputDir;
      this.junitOutputDir = junitOutputDir;
    }
  }

  public static final class RunResult {
    public final TestSummary summary;

    RunResult(TestSummary summary) {
      this.summary = summary;
    }
  }

  private static final class NullResultsRepository implements JavaFormatter.ResultsRepository {
    @Override
    public void open(String string) {
    }

    @Override
    public void write(String content) {
    }

    @Override
    public void close() throws IOException {
    }
  }
}
