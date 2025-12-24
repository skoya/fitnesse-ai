package fitnesse.testsystems.slim;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import fitnesse.slim.SlimServer;
import fitnesse.slim.SlimStreamReader;
import fitnesse.slim.SlimVersion;
import fitnesse.slim.instructions.Instruction;
import fitnesse.slim.protocol.SlimDeserializer;
import fitnesse.slim.protocol.SlimListBuilder;
import fitnesse.slim.protocol.SlimSerializer;
import fitnesse.testsystems.ExecutionLogListener;
import fitnesse.util.MockSocket;
import fitnesse.util.VertxWorkerPool;

import static fitnesse.testsystems.slim.SlimCommandRunningClient.resultToMap;

public class InProcessSlimClient implements SlimClient {
  private final String testSystemName;
  private final SlimServer slimServer;
  private final ExecutionLogListener executionLogListener;
  private ClassLoader classLoader;
  private MockSocket socket;
  private PipedOutputStream clientOutput;
  private java.util.concurrent.CompletableFuture<Void> slimServerTask;
  private SlimStreamReader reader;
  private double slimServerVersion;

  public InProcessSlimClient(String testSystemName, SlimServer slimServer, ExecutionLogListener executionLogListener, ClassLoader classLoader) {
    this.testSystemName = testSystemName;
    this.slimServer = slimServer;
    this.executionLogListener = executionLogListener;
    this.classLoader = classLoader;
  }

  @Override
  public void start() throws IOException, SlimVersionMismatch {
    commandStarted();

    PipedInputStream socketInput = new PipedInputStream();
    clientOutput = new PipedOutputStream(socketInput);
    PipedInputStream clientInput = new PipedInputStream();
    PipedOutputStream socketOutput = new PipedOutputStream(clientInput);
    reader = new SlimStreamReader(clientInput);
    socket = new MockSocket(socketInput, socketOutput);
    // Start SlimServer in a worker thread
    slimServerTask = VertxWorkerPool.runDedicated("fitnesse-slim-inproc-" + System.identityHashCode(this), () -> {
      ClassLoader original = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(classLoader);
        slimServer.serve(socket);
        executionLogListener.exitCode(0);
      } catch (Throwable t) { // NOSONAR
        // This point is not reached since no errors bubble up this far
        executionLogListener.exceptionOccurred(t);
      } finally {
        Thread.currentThread().setContextClassLoader(original);
      }
    });
    connect();
  }

  private void commandStarted() {
    executionLogListener.commandStarted(new ExecutionLogListener.ExecutionContext() {
      @Override
      public String getCommand() {
        return "";
      }

      @Override
      public String getTestSystemName() {
        return testSystemName;
      }
    });
  }

  @Override
  public Map<String, Object> invokeAndGetResponse(List<Instruction> statements) throws SlimCommunicationException {
    if (statements.isEmpty())
      return Collections.emptyMap();
    String instructions = SlimSerializer.serialize(new SlimListBuilder(slimServerVersion).toList(statements));
    String results;
    try {
      SlimStreamReader.sendSlimMessage(clientOutput, instructions);
      results = reader.getSlimMessage();
    } catch (IOException e) {
      throw new SlimCommunicationException("Could not send/receive data with SUT", e);
    }
    List<Object> resultList = SlimDeserializer.deserialize(results);
    return resultToMap(resultList);
  }

  @Override
  public void connect() throws IOException, SlimVersionMismatch {
    String slimServerVersionMessage = reader.readLine();
    if (!isConnected(slimServerVersionMessage)) {
      throw new SlimVersionMismatch("Got invalid slim header from client. Read the following: " + slimServerVersionMessage);
    }

    slimServerVersion = Double.parseDouble(slimServerVersionMessage.replace(SlimVersion.SLIM_HEADER, ""));
    if (slimServerVersion == SlimCommandRunningClient.NO_SLIM_SERVER_CONNECTION_FLAG) {
      throw new SlimVersionMismatch("Slim Protocol Version Error: Server did not respond with a valid version number.");
    } else if (slimServerVersion < SlimCommandRunningClient.MINIMUM_REQUIRED_SLIM_VERSION) {
      throw new SlimVersionMismatch(String.format("Slim Protocol Version Error: Expected V%s but was V%s", SlimCommandRunningClient.MINIMUM_REQUIRED_SLIM_VERSION, slimServerVersion));
    }
  }

  public boolean isConnected(String slimServerVersionMessage) {
    return slimServerVersionMessage.startsWith(SlimVersion.SLIM_HEADER);
  }

  @Override
  public void bye() throws IOException {
    // Close slimServer thread
    if (slimServerTask != null && !slimServerTask.isDone()) {
      SlimStreamReader.sendSlimMessage(clientOutput, SlimVersion.BYEMESSAGE);
    }
  }

  @Override
  public void kill() {
    if (socket != null && !socket.isClosed()) {
      socket.close();
    }
    if (slimServerTask != null) {
      slimServerTask.cancel(true);
    }
  }
}
