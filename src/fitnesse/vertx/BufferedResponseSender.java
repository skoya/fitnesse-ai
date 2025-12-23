package fitnesse.vertx;

import fitnesse.http.ResponseSender;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class BufferedResponseSender implements ResponseSender {
  private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

  @Override
  public void send(byte[] bytes) throws IOException {
    buffer.write(bytes);
  }

  @Override
  public void close() {
    // No resources to close.
  }

  byte[] toByteArray() {
    return buffer.toByteArray();
  }
}
