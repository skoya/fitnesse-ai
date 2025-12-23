package fitnesse.vertx;

import fitnesse.responders.run.SuiteResponder;
import fitnesse.responders.run.TestResponder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;

import java.util.concurrent.TimeUnit;

/**
 * Worker verticle that handles Slim/Fit execution over the EventBus using a bounded worker pool.
 */
final class TestRunnerVerticle extends AbstractVerticle {
  private final ResponderBusService busService;
  private final RunMonitor monitor;
  private final VertxConfig config;
  private WorkerExecutor executor;

  TestRunnerVerticle(ResponderBusService busService, RunMonitor monitor, VertxConfig config) {
    this.busService = busService;
    this.monitor = monitor;
    this.config = config;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    executor = vertx.createSharedWorkerExecutor(
      "fitnesse-test-runner",
      config.testPoolSize(),
      config.requestTimeoutMillis(),
      TimeUnit.MILLISECONDS);

    busService.registerWithExecutor(vertx.eventBus(), "fitnesse.test.suite",
      new SuiteResponder(), executor, monitor, config.testMaxQueue());
    busService.registerWithExecutor(vertx.eventBus(), "fitnesse.test.single",
      new TestResponder(), executor, monitor, config.testMaxQueue());
    startPromise.complete();
  }

  @Override
  public void stop() {
    if (executor != null) {
      executor.close();
    }
  }
}
