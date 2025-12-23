package fitnesse.docstore;

import java.time.Instant;

public final class PageHistoryEntry {
  private final String commitId;
  private final String author;
  private final String authorEmail;
  private final String message;
  private final Instant timestamp;

  public PageHistoryEntry(String commitId, String author, String message, Instant timestamp) {
    this(commitId, author, null, message, timestamp);
  }

  public PageHistoryEntry(String commitId, String author, String authorEmail, String message, Instant timestamp) {
    this.commitId = commitId;
    this.author = author;
    this.authorEmail = authorEmail;
    this.message = message;
    this.timestamp = timestamp;
  }

  public String commitId() {
    return commitId;
  }

  public String author() {
    return author;
  }

  public String authorEmail() {
    return authorEmail;
  }

  public String message() {
    return message;
  }

  public Instant timestamp() {
    return timestamp;
  }
}
