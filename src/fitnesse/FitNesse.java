// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import fitnesse.http.MockRequestBuilder;
import fitnesse.http.MockResponseSender;
import fitnesse.http.Request;
import fitnesse.http.Response;
import fitnesse.socketservice.SocketService;
import fitnesse.socketservice.VertxSocketService;
import fitnesse.util.MockSocket;
import fitnesse.util.SerialExecutorService;
import fitnesse.util.VertxWorkerPool;

public class FitNesse {
  private static final Logger LOG = Logger.getLogger(FitNesse.class.getName());

  private final FitNesseContext context;
  private final ExecutorService executorService;
  private volatile AutoCloseable theService;

  public FitNesse(FitNesseContext context) {
    this.context = context;
    this.executorService = VertxWorkerPool.newExecutor("fitnesse-legacy-worker", this.context.maximumWorkers);
  }

  public void start(ServerSocket serverSocket) throws IOException {
    theService = new SocketService(new FitNesseServer(context, executorService), false, serverSocket);
  }

  public void startNetServer(io.vertx.core.net.NetServerOptions options, int port) throws IOException {
    theService = new VertxSocketService(new FitNesseServer(context, executorService), options, port);
  }

  public synchronized void stop() throws IOException {
    if (theService != null) {
      try {
        theService.close();
      } catch (Exception e) {
        if (e instanceof IOException) {
          throw (IOException) e;
        }
        throw new IOException("Failed to close FitNesse service", e);
      }
      theService = null;
    }
    if (!executorService.isShutdown()) {
      executorService.shutdown();
    }
  }

  public boolean isRunning() {
    return theService != null;
  }

  public void executeSingleCommand(String command, OutputStream out) throws Exception {
    Request request = new MockRequestBuilder(command).noChunk().build();
    FitNesseExpediter expediter = new FitNesseExpediter(new MockSocket(), context, new SerialExecutorService());
    Response response = expediter.createGoodResponse(request);
    int responseStatus = response.getStatus();
    if (responseStatus >= 400 && responseStatus <= 599){
        throw new Exception("error loading page: " + responseStatus);
    }
    response.withoutHttpHeaders();
    MockResponseSender sender = new MockResponseSender(out);
    sender.doSending(response);
  }

}
