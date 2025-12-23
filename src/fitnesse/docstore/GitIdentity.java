package fitnesse.docstore;

/**
 * Logical author identity for git commits.
 */
public final class GitIdentity {
  private final String name;
  private final String email;

  public GitIdentity(String name, String email) {
    this.name = name;
    this.email = email;
  }

  public String name() {
    return name;
  }

  public String email() {
    return email;
  }

  public boolean isEmpty() {
    return (name == null || name.isBlank()) && (email == null || email.isBlank());
  }
}
