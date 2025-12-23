package fitnesse.docstore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GitRepository {
  private final Path repoRoot;

  public GitRepository(Path repoRoot) {
    this.repoRoot = repoRoot;
  }

  public void init() {
    run("init");
  }

  public void add(List<Path> paths) {
    List<String> args = new ArrayList<>();
    args.add("add");
    for (Path path : paths) {
      args.add(relative(path));
    }
    run(args.toArray(new String[0]));
  }

  public void commit(String message) {
    commit(message, null, GitCommitConfig.fromEnv());
  }

  public void commit(String message, GitIdentity author, GitCommitConfig commitConfig) {
    List<String> args = List.of("commit", "-m", message, "--allow-empty");
    runWithEnv(args, author, commitConfig);
  }

  public String currentCommit() {
    return run("rev-parse", "HEAD");
  }

  public String currentBranch() {
    return run("rev-parse", "--abbrev-ref", "HEAD");
  }

  public void checkout(String ref) {
    run("checkout", ref);
  }

  public void createBranch(String name, String startPoint) {
    run("branch", name, startPoint);
  }

  public void deleteBranch(String name) {
    run("branch", "-D", name);
  }

  public void mergeFastForward(String branch) {
    run("merge", "--ff-only", branch);
  }

  public void mergeNoFastForward(String branch) {
    run("merge", "--no-ff", branch);
  }

  public void mergeOurs(String branch) {
    run("merge", "-s", "ours", branch);
  }

  public void mergeTheirs(String branch) {
    run("merge", "-X", "theirs", branch);
  }

  public void mergeSquash(String branch) {
    run("merge", "--squash", branch);
  }

  public void rebase(String upstream) {
    run("rebase", upstream);
  }

  public void abortMerge() {
    run("merge", "--abort");
  }

  public void abortRebase() {
    run("rebase", "--abort");
  }

  public List<String> log(String path, int limit) {
    String max = String.valueOf(Math.max(1, limit));
    String output = run("log", "--date=iso-strict", "--pretty=format:%H|%an|%ae|%ad|%s", "-" + max, "--", path);
    List<String> lines = new ArrayList<>();
    if (output == null || output.isEmpty()) {
      return lines;
    }
    for (String line : output.split("\\r?\\n")) {
      if (!line.isEmpty()) {
        lines.add(line);
      }
    }
    return lines;
  }

  public String latestCommit(String path) {
    String output = run("log", "-n", "1", "--pretty=format:%H", "--", path);
    return output == null || output.isEmpty() ? null : output.trim();
  }

  public String diff(String path, String commitId) {
    return run("diff", commitId + "^", commitId, "--", path);
  }

  public void checkout(String commitId, String path) {
    run("checkout", commitId, "--", path);
  }

  private String relative(Path path) {
    return repoRoot.relativize(path).toString().replace("\\", "/");
  }

  private String run(String... args) {
    return runWithEnv(List.of(args), null, null);
  }

  private String runWithEnv(List<String> args, GitIdentity author, GitCommitConfig commitConfig) {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.addAll(args);
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(repoRoot.toFile());
    builder.redirectErrorStream(true);
    if (commitConfig != null) {
      if (commitConfig.committerName() != null) {
        builder.environment().put("GIT_COMMITTER_NAME", commitConfig.committerName());
      }
      if (commitConfig.committerEmail() != null) {
        builder.environment().put("GIT_COMMITTER_EMAIL", commitConfig.committerEmail());
      }
    }
    if (author != null && !author.isEmpty()) {
      if (author.name() != null) {
        builder.environment().put("GIT_AUTHOR_NAME", author.name());
      }
      if (author.email() != null) {
        builder.environment().put("GIT_AUTHOR_EMAIL", author.email());
      }
    }
    try {
      Process process = builder.start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exit = process.waitFor();
      if (exit != 0) {
        throw new IllegalStateException("git command failed: " + String.join(" ", command) + "\n" + output);
      }
      return output.trim();
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("git command failed: " + String.join(" ", command), e);
    }
  }
}
