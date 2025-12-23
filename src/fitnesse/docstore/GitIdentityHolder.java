package fitnesse.docstore;

/**
 * Thread-local identity to stamp git authorship when a request is in-flight.
 * Upstream layers (e.g., auth filters) can set/clear this.
 */
public final class GitIdentityHolder {
  private static final ThreadLocal<GitIdentity> CURRENT = new ThreadLocal<>();

  private GitIdentityHolder() {}

  public static void set(GitIdentity identity) {
    CURRENT.set(identity);
  }

  public static GitIdentity current() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
