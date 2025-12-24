// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.socketservice;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import fitnesse.util.VertxWorkerPool;

/**
 * Runs a separate service (thread) to handle new connections.
 */
public class SocketService implements AutoCloseable {
  private static final Logger LOG = Logger.getLogger(SocketService.class.getName());

  private final ServerSocket serverSocket;
  private final CompletableFuture<Void> serviceTask;
  private final CountDownLatch started = new CountDownLatch(1);
  private volatile boolean running = false;
  private final SocketServer server;

  private volatile boolean everRan = false;

  public SocketService(SocketServer server, boolean daemon, ServerSocket serverSocket) throws IOException {
    this.server = server;
    this.serverSocket = serverSocket;
    String name = "fitnesse-socket-service-" + System.identityHashCode(this);
    this.serviceTask = VertxWorkerPool.runDedicated(name, this::serviceThread);
  }

  public void close() throws IOException {
    waitForServiceThreadToStart();
    running = false;
    serverSocket.close();
    try {
      serviceTask.get();
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Service task join interrupted", e);
      Thread.currentThread().interrupt();
    }
  }

  private void waitForServiceThreadToStart() {
    if (everRan) {
      return;
    }
    try {
      started.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void serviceThread() {
    running = true;
    started.countDown();
    while (running) {
      try {
        Socket s = serverSocket.accept();
        everRan = true;
        server.serve(s);
      } catch (java.lang.OutOfMemoryError e) {
        LOG.log(Level.SEVERE, "Can't create new thread.  Out of Memory.  Aborting.", e);
        System.exit(99);
      } catch (SocketException sox) {
        running = false;
      } catch (IOException e) {
        LOG.log(Level.SEVERE, "I/O exception in service thread", e);
      }
    }
  }
}
