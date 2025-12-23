package fitnesse.vertx;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class ResponderResponseParser {
  private ResponderResponseParser() {
  }

  static ParsedResponse parse(byte[] raw) {
    int headerEnd = indexOf(raw, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
    if (headerEnd < 0) {
      headerEnd = indexOf(raw, "\n\n".getBytes(StandardCharsets.ISO_8859_1));
    }

    if (headerEnd < 0) {
      return new ParsedResponse(200, new LinkedHashMap<>(), raw);
    }

    String headerText = new String(raw, 0, headerEnd, StandardCharsets.ISO_8859_1);
    String[] lines = headerText.split("\\r?\\n");
    int status = 200;
    Map<String, String> headers = new LinkedHashMap<>();

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (i == 0 && line.startsWith("HTTP/")) {
        String[] parts = line.split(" ");
        if (parts.length >= 2) {
          try {
            status = Integer.parseInt(parts[1]);
          } catch (NumberFormatException e) {
            status = 200;
          }
        }
        continue;
      }
      int sep = line.indexOf(':');
      if (sep > 0) {
        String key = line.substring(0, sep).trim();
        String value = line.substring(sep + 1).trim();
        headers.put(key, value);
      }
    }

    int bodyStart = headerEnd + (raw[headerEnd] == '\r' ? 4 : 2);
    byte[] body = new byte[Math.max(0, raw.length - bodyStart)];
    System.arraycopy(raw, bodyStart, body, 0, body.length);
    return new ParsedResponse(status, headers, body);
  }

  private static int indexOf(byte[] array, byte[] target) {
    outer:
    for (int i = 0; i <= array.length - target.length; i++) {
      for (int j = 0; j < target.length; j++) {
        if (array[i + j] != target[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  static final class ParsedResponse {
    final int status;
    final Map<String, String> headers;
    final byte[] body;

    private ParsedResponse(int status, Map<String, String> headers, byte[] body) {
      this.status = status;
      this.headers = headers;
      this.body = body;
    }
  }
}
