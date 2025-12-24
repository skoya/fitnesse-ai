package fitnesse.socketservice;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import fitnesse.util.VertxWorkerPool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

final class VertxSocketAdapter extends Socket {
  private static final Logger LOG = Logger.getLogger(VertxSocketAdapter.class.getName());
  private static final int PIPE_BUFFER_BYTES = 64 * 1024;

  private final NetSocket netSocket;
  private final InputStream input;
  private final OutputStream output;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final java.net.SocketAddress remoteAddress;
  private final java.net.SocketAddress localAddress;

  VertxSocketAdapter(NetSocket netSocket) throws IOException {
    this.netSocket = netSocket;
    PipedInputStream pipedInput = new PipedInputStream(PIPE_BUFFER_BYTES);
    PipedOutputStream pipedOutput = new PipedOutputStream(pipedInput);
    this.input = pipedInput;
    this.output = new NetSocketOutputStream(netSocket);

    SocketAddress remote = netSocket.remoteAddress();
    SocketAddress local = netSocket.localAddress();
    this.remoteAddress = toInet(remote, "remote");
    this.localAddress = toInet(local, "local");

    netSocket.handler(buffer -> VertxWorkerPool.runDaemon(() -> writeToPipe(pipedOutput, buffer)));
    netSocket.closeHandler(ignored -> closePipe(pipedOutput));
    netSocket.exceptionHandler(err -> {
      LOG.log(Level.FINE, "NetSocket error", err);
      closePipe(pipedOutput);
    });
  }

  @Override
  public synchronized InputStream getInputStream() {
    return input;
  }

  @Override
  public synchronized OutputStream getOutputStream() {
    return output;
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    try {
      input.close();
    } catch (IOException ignored) {
      // Ignore close errors.
    }
    try {
      output.close();
    } catch (IOException ignored) {
      // Ignore close errors.
    }
    netSocket.close();
  }

  @Override
  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public java.net.SocketAddress getRemoteSocketAddress() {
    return remoteAddress;
  }

  @Override
  public java.net.SocketAddress getLocalSocketAddress() {
    return localAddress;
  }

  private void writeToPipe(PipedOutputStream output, Buffer buffer) {
    try {
      output.write(buffer.getBytes());
      output.flush();
    } catch (IOException e) {
      closePipe(output);
    }
  }

  private void closePipe(PipedOutputStream output) {
    try {
      output.close();
    } catch (IOException ignored) {
      // Ignore close errors.
    }
  }

  private java.net.SocketAddress toInet(SocketAddress address, String fallbackHost) {
    if (address == null) {
      return new InetSocketAddress(fallbackHost, 0);
    }
    return new InetSocketAddress(address.host(), address.port());
  }

  private static final class NetSocketOutputStream extends OutputStream {
    private final NetSocket socket;

    private NetSocketOutputStream(NetSocket socket) {
      this.socket = socket;
    }

    @Override
    public void write(int b) {
      socket.write(Buffer.buffer(new byte[] { (byte) b }));
    }

    @Override
    public void write(byte[] b, int off, int len) {
      socket.write(Buffer.buffer(Arrays.copyOfRange(b, off, off + len)));
    }

    @Override
    public void close() {
      socket.close();
    }
  }
}
