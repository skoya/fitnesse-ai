package fitnesse.ai;

import io.vertx.core.Future;

import java.util.List;

/**
 * Minimal multi-step workflow runner as a placeholder for LangGraph.
 */
public final class SequentialWorkflow {
  private final List<WorkflowStep> steps;

  public SequentialWorkflow(List<WorkflowStep> steps) {
    this.steps = steps;
  }

  public Future<String> run(String input) {
    Future<String> result = Future.succeededFuture(input);
    for (WorkflowStep step : steps) {
      result = result.compose(step::run);
    }
    return result;
  }
}
