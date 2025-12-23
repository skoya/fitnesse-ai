package fitnesse.wikitext.diagram;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

public final class PlantUmlEncoder {
  private PlantUmlEncoder() {
  }

  public static String encode(String source) {
    byte[] input = source.getBytes(StandardCharsets.UTF_8);
    byte[] compressed = deflate(input);
    return encode64(compressed);
  }

  private static byte[] deflate(byte[] input) {
    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
    deflater.setInput(input);
    deflater.finish();
    byte[] buffer = new byte[1024];
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    while (!deflater.finished()) {
      int count = deflater.deflate(buffer);
      output.write(buffer, 0, count);
    }
    deflater.end();
    return output.toByteArray();
  }

  private static String encode64(byte[] data) {
    StringBuilder encoded = new StringBuilder();
    int i = 0;
    while (i < data.length) {
      int b1 = data[i++] & 0xff;
      int b2 = i < data.length ? data[i++] & 0xff : 0;
      int b3 = i < data.length ? data[i++] & 0xff : 0;
      append3bytes(encoded, b1, b2, b3);
    }
    return encoded.toString();
  }

  private static void append3bytes(StringBuilder encoded, int b1, int b2, int b3) {
    int c1 = (b1 >> 2) & 0x3f;
    int c2 = ((b1 & 0x3) << 4) | ((b2 >> 4) & 0xf);
    int c3 = ((b2 & 0xf) << 2) | ((b3 >> 6) & 0x3);
    int c4 = b3 & 0x3f;
    encoded.append(encode6bit(c1))
      .append(encode6bit(c2))
      .append(encode6bit(c3))
      .append(encode6bit(c4));
  }

  private static char encode6bit(int b) {
    if (b < 10) {
      return (char) ('0' + b);
    }
    b -= 10;
    if (b < 26) {
      return (char) ('A' + b);
    }
    b -= 26;
    if (b < 26) {
      return (char) ('a' + b);
    }
    b -= 26;
    if (b == 0) {
      return '-';
    }
    if (b == 1) {
      return '_';
    }
    return '?';
  }
}
