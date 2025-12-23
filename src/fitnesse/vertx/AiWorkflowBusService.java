package fitnesse.vertx;

import fitnesse.ai.AiWorkflow;
import fitnesse.ai.AiWorkflowService;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

/**
 * EventBus facade for workflow persistence and execution.
 */
final class AiWorkflowBusService {
  static final String ADDRESS_LIST = "fitnesse.ai.workflows.list";
  static final String ADDRESS_GET = "fitnesse.ai.workflows.get";
  static final String ADDRESS_SAVE = "fitnesse.ai.workflows.save";
  static final String ADDRESS_RUN = "fitnesse.ai.workflows.run";
  static final String ADDRESS_RUNS = "fitnesse.ai.workflows.runs";

  private final AiWorkflowService service;

  AiWorkflowBusService(AiWorkflowService service) {
    this.service = service;
  }

  void register(EventBus bus) {
    bus.consumer(ADDRESS_LIST, message -> service.list()
      .onSuccess(list -> message.reply(new JsonObject().put("workflows", list)))
      .onFailure(err -> message.fail(500, err.getMessage())));

    bus.consumer(ADDRESS_GET, message -> {
      JsonObject payload = (JsonObject) message.body();
      String id = payload.getString("id", "");
      service.load(id)
        .onSuccess(flow -> message.reply(flow.toJson()))
        .onFailure(err -> message.fail(404, err.getMessage()));
    });

    bus.consumer(ADDRESS_SAVE, message -> {
      JsonObject payload = (JsonObject) message.body();
      AiWorkflow workflow = AiWorkflow.fromJson(payload);
      service.save(workflow)
        .onSuccess(flow -> message.reply(flow.toJson()))
        .onFailure(err -> message.fail(500, err.getMessage()));
    });

    bus.consumer(ADDRESS_RUN, message -> {
      JsonObject payload = (JsonObject) message.body();
      AiWorkflow workflow = AiWorkflow.fromJson(payload.getJsonObject("workflow"));
      service.run(workflow)
        .onSuccess(result -> message.reply(result.toJson()))
        .onFailure(err -> message.fail(500, err.getMessage()));
    });

    bus.consumer(ADDRESS_RUNS, message -> {
      JsonObject payload = (JsonObject) message.body();
      String id = payload.getString("id", "");
      int limit = payload.getInteger("limit", 10);
      service.listRuns(id, limit)
        .onSuccess(runs -> message.reply(new JsonObject().put("runs", runs)))
        .onFailure(err -> message.fail(500, err.getMessage()));
    });
  }
}
