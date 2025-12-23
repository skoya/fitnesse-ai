package fitnesse.docstore;

public class DocStoreConflictException extends RuntimeException {
  public DocStoreConflictException(String message) {
    super(message);
  }
}
