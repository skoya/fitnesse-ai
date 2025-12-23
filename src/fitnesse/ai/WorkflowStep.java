package fitnesse.ai;

import io.vertx.core.Future;

/**
 * Describes a single AI workflow step.
 */
public interface WorkflowStep {
  Future<String> run(String input);
}
