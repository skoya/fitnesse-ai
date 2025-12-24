// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.testsystems.fit;

import fitnesse.socketservice.PlainServerSocketFactory;
import fitnesse.socketservice.SocketService;
import fitnesse.testsystems.CommandRunner;
import fitnesse.testsystems.ExecutionLogListener;
import fitnesse.testsystems.MockCommandRunner;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import fitnesse.util.VertxWorkerPool;

public class CommandRunningFitClient extends FitClient {
  private static final Logger LOG = Logger.getLogger(CommandRunningFitClient.class.getName());
  public static int TIMEOUT = 60000;

  private final int ticketNumber;
  private final CommandRunningStrategy commandRunningStrategy;
  private boolean connectionEstablished = false;
  private SocketService server;

  public CommandRunningFitClient(CommandRunningStrategy commandRunningStrategy) {
    super();
    this.ticketNumber = generateTicketNumber();
    this.commandRunningStrategy = commandRunningStrategy;
  }

  private int generateTicketNumber() {
    return 0xF17;
  }

  public void start() throws IOException {
    ServerSocket serverSocket = new PlainServerSocketFactory().createServerSocket(0);
    server = new SocketService(new SocketCatcher(this, ticketNumber), true, serverSocket);
    int port = serverSocket.getLocalPort();
    try {
      commandRunningStrategy.start(this, port, ticketNumber);
      waitForConnection();
    } catch (Exception e) {
      exceptionOccurred(e);
    }
  }

  @Override
  public void acceptSocket(Socket s) throws IOException, InterruptedException {
    super.acceptSocket(s);
    connectionEstablished = true;
    synchronized (this) {
      notify();
    }
  }

  private void waitForConnection() throws InterruptedException {
    while (!isSuccessfullyStarted()) {
      Thread.sleep(100);
      checkForPulse();
    }
  }

  public boolean isConnectionEstablished() {
    return connectionEstablished;
  }

  @Override
  public void join() {
    try {
      commandRunningStrategy.join();
      super.join();

      commandRunningStrategy.kill();
    } finally {
      closeServer();
    }
  }

  private void closeServer() {
    try {
      server.close();
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Unable to close FitClient socket server", e);
    }
  }

  @Override
  public void kill() {
    super.kill();
    commandRunningStrategy.kill();
  }

  public interface CommandRunningStrategy {
    void start(CommandRunningFitClient fitClient, int port, int ticketNumber) throws IOException;

    void join();

    void kill();
  }

  /** Runs commands by starting a new process. */
  public static class OutOfProcessCommandRunner implements CommandRunningStrategy {

    private final String[] command;
    private final Map<String, String> environmentVariables;
    private final ExecutionLogListener executionLogListener;
    private Long timeoutTimerId;
    private Long earlyTerminationTimerId;
    private CommandRunner commandRunner;

    public OutOfProcessCommandRunner(String[] command, Map<String, String> environmentVariables, ExecutionLogListener executionLogListener) {
      this.command = command;
      this.environmentVariables = environmentVariables;
      this.executionLogListener = executionLogListener;
    }

    private void makeCommandRunner(int port, int ticketNumber) throws UnknownHostException {
      String[] fitArguments = { getLocalhostName(), Integer.toString(port), Integer.toString(ticketNumber) };
      String[] commandLine = ArrayUtils.addAll(command, fitArguments);
      commandRunner = new CommandRunner(commandLine, environmentVariables, executionLogListener);
    }

    @Override
    public void start(CommandRunningFitClient fitClient, int port, int ticketNumber) throws IOException {
      makeCommandRunner(port, ticketNumber);
      commandRunner.asynchronousStart();
      timeoutTimerId = VertxWorkerPool.vertx().setTimer(TIMEOUT, id -> new TimeoutRunnable(fitClient).run());
      earlyTerminationTimerId = VertxWorkerPool.vertx().setTimer(1000, id ->
        VertxWorkerPool.run(new EarlyTerminationRunnable(fitClient, commandRunner)));
    }

    @Override
    public void join() {
      commandRunner.join();
      killVigilantThreads();
    }

    @Override
    public void kill() {
      commandRunner.kill();
      killVigilantThreads();
    }

    private void killVigilantThreads() {
      if (timeoutTimerId != null) {
        VertxWorkerPool.vertx().cancelTimer(timeoutTimerId);
      }
      if (earlyTerminationTimerId != null) {
        VertxWorkerPool.vertx().cancelTimer(earlyTerminationTimerId);
      }
    }

    private static class TimeoutRunnable implements Runnable {

      private final FitClient fitClient;

      public TimeoutRunnable(FitClient fitClient) {
        this.fitClient = fitClient;
      }

      @Override
      public void run() {
        synchronized (this.fitClient) {
          if (!fitClient.isSuccessfullyStarted()) {
            fitClient.notify();
            fitClient.exceptionOccurred(new Exception(
                "FitClient communication socket was not received on time"));
          }
        }
      }
    }

    private static class EarlyTerminationRunnable implements Runnable {
      private final CommandRunningFitClient fitClient;
      private final CommandRunner commandRunner;

      EarlyTerminationRunnable(CommandRunningFitClient fitClient, CommandRunner commandRunner) {
        this.fitClient = fitClient;
        this.commandRunner = commandRunner;
      }

      @Override
      public void run() {
        try {
          commandRunner.waitForCommandToFinish();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt(); // remember interrupted
          return;
        }
        synchronized (fitClient) {
          if (!fitClient.isConnectionEstablished()) {
            fitClient.notify();
            Exception e = new Exception(
                    "FitClient external process terminated before a connection could be established");
            // TODO: use executionLogListener.exceptionOccurred(e)
            commandRunner.exceptionOccurred(e);
            fitClient.exceptionOccurred(e);
          }
        }
      }
    }
  }

  /** Runs commands in fast mode (in-process). */
  public static class InProcessCommandRunner implements CommandRunningStrategy {
    private final Method testRunnerMethod;
    private final ExecutionLogListener executionLogListener;
    private ClassLoader classLoader;
    private java.util.concurrent.CompletableFuture<Void> fastFitServerTask;
    private MockCommandRunner commandRunner;

    public InProcessCommandRunner(Method testRunnerMethod, ExecutionLogListener executionLogListener, ClassLoader classLoader) {
      this.testRunnerMethod = testRunnerMethod;
      this.executionLogListener = executionLogListener;
      this.classLoader = classLoader;
    }

    @Override
    public void start(CommandRunningFitClient fitClient, int port, int ticketNumber) throws IOException {
      String[] arguments = new String[] { "-x", getLocalhostName(), Integer.toString(port), Integer.toString(ticketNumber) };
      this.fastFitServerTask = runTestRunner(testRunnerMethod, arguments);
      commandRunner = new MockCommandRunner(executionLogListener);
      commandRunner.asynchronousStart();
    }

    @Override
    public void join() {
      try {
        if (fastFitServerTask != null) {
          fastFitServerTask.get();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // remember interrupted
      } catch (Exception e) {
        LOG.log(Level.WARNING, "In-process test runner failed", e);
      }
    }

    @Override
    public void kill() {
      commandRunner.kill();
      if (fastFitServerTask != null) {
        fastFitServerTask.cancel(true);
      }
    }

    protected java.util.concurrent.CompletableFuture<Void> runTestRunner(final Method testRunnerMethod, final String[] args) {
      return VertxWorkerPool.runDedicated("fitnesse-fit-inproc-" + System.identityHashCode(this), () -> {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
          Thread.currentThread().setContextClassLoader(classLoader);
          testRunnerMethod.invoke(null, (Object) args);
        } catch (IllegalAccessException | InvocationTargetException e) {
          LOG.log(Level.WARNING, "Could not start in-process test runner", e);
        } finally {
          Thread.currentThread().setContextClassLoader(original);
        }
      });
    }
  }

  private static String getLocalhostName() throws UnknownHostException {
    return java.net.InetAddress.getLocalHost().getHostName();
  }

}
