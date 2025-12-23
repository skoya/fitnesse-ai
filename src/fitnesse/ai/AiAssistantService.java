package fitnesse.ai;

import io.vertx.core.Future;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Coordinates provider execution and persistence of AI history.
 */
public final class AiAssistantService {
  private final AiProvider provider;
  private final AiHistoryStore historyStore;

  public AiAssistantService(AiProvider provider, AiHistoryStore historyStore) {
    this.provider = provider;
    this.historyStore = historyStore;
  }

  /**
   * Executes an assist request and persists the interaction.
   */
  public Future<AiResponse> assist(AiRequest request) {
    if ("test-gen".equalsIgnoreCase(request.tool())) {
      AiResponse response = buildTestGenResponse(request);
      return historyStore.append(request, response).map(response);
    }
    return provider.generate(request).compose(response ->
      historyStore.append(request, response).map(response));
  }

  /**
   * Convenience factory for simple local usage.
   */
  public static AiRequest buildRequest(String prompt, List<String> grounding, String tool,
                                       Map<String, String> parameters, String conversationId) {
    return new AiRequest(prompt, grounding, tool, parameters, conversationId, java.time.Instant.now());
  }

  private AiResponse buildTestGenResponse(AiRequest request) {
    String page = request.parameters() == null ? "" : request.parameters().getOrDefault("pagePath", "");
    String fixture = request.parameters() == null ? "" : request.parameters().getOrDefault("fixture", "");
    StringBuilder response = new StringBuilder();
    String className = page.isEmpty() ? "GeneratedTest" : page.replace('.', '_') + "Test";
    response.append("Test generation stub. Provide an OpenAI adapter to enable real generation.\\n\\n");
    response.append("JUnit test:\\n");
    response.append(TestGenTemplates.junitTestTemplate(className, fixture)).append("\n");
    response.append("Fixture:\\n");
    response.append(TestGenTemplates.fixtureTemplate(fixture));
    return new AiResponse(response.toString(), request.conversationId(), Instant.now());
  }
}
