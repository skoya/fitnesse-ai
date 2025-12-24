package fitnesse.socketservice;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VertxSocketService implements AutoCloseable {
  private static final Logger LOG = Logger.getLogger(VertxSocketService.class.getName());
  private static final long START_TIMEOUT_MS = 5000L;

  private final Vertx vertx;
  private final NetServer server;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public VertxSocketService(SocketServer handler, NetServerOptions options, int port) throws IOException {
    this.vertx = Vertx.vertx();
    this.server = vertx.createNetServer(options);
    this.server.connectHandler(netSocket -> {
      try {
        handler.serve(new VertxSocketAdapter(netSocket));
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Failed to handle legacy socket connection", e);
        netSocket.close();
      }
    });

    CountDownLatch latch = new CountDownLatch(1);
    String host = options.getHost();
    (host == null ? server.listen(port) : server.listen(port, host)).onComplete(ar -> {
      if (ar.succeeded()) {
        latch.countDown();
      } else {
        LOG.log(Level.SEVERE, "Failed to start legacy NetServer", ar.cause());
        latch.countDown();
      }
    });

    try {
      if (!latch.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        throw new IOException("Timed out starting legacy NetServer");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while starting legacy NetServer", e);
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    server.close();
    vertx.close();
  }
}
