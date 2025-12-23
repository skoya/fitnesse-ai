package fitnesse.vertx;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class RunMonitorTest {

  @Test
  public void tracksQueuedRunningCompletedAndAverage() throws Exception {
    RunMonitor monitor = new RunMonitor();
    assertThat(monitor.canAccept(1), is(true));
    monitor.incrementQueued();
    assertThat(monitor.snapshot().getInteger("queued"), is(1));

    long start = monitor.startRun();
    Thread.sleep(5);
    monitor.finishRun(start);

    JsonObject snap = monitor.snapshot();
    assertThat(snap.getInteger("queued"), is(0));
    assertThat(snap.getInteger("running"), is(0));
    assertThat(snap.getLong("completed"), is(1L));
    assertThat(snap.getLong("averageMillis"), greaterThanOrEqualTo(0L));
  }

  @Test
  public void respectsMaxQueue() {
    RunMonitor monitor = new RunMonitor();
    assertThat(monitor.canAccept(1), is(true));
    monitor.incrementQueued();
    assertThat(monitor.canAccept(1), is(false));
    assertThat(monitor.canAccept(0), is(true)); // unlimited when <=0
  }
}
