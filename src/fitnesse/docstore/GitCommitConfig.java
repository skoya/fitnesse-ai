package fitnesse.docstore;

final class GitCommitConfig {
  private final String committerName;
  private final String committerEmail;

  private GitCommitConfig(String committerName, String committerEmail) {
    this.committerName = committerName;
    this.committerEmail = committerEmail;
  }

  static GitCommitConfig fromEnv() {
    String name = firstNonBlank(System.getProperty("docstore.git.committer.name"),
      System.getenv("FITNESSE_GIT_COMMITTER_NAME"),
      "fitnesse-bot");
    String email = firstNonBlank(System.getProperty("docstore.git.committer.email"),
      System.getenv("FITNESSE_GIT_COMMITTER_EMAIL"),
      "fitnesse-bot@example.invalid");
    return new GitCommitConfig(name, email);
  }

  String committerName() {
    return committerName;
  }

  String committerEmail() {
    return committerEmail;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }
}
