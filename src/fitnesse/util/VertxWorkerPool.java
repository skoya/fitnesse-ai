package fitnesse.util;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.WorkerExecutor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VertxWorkerPool {
  private static final Vertx VERTX =
    Vertx.vertx(new VertxOptions().setUseDaemonThread(Boolean.TRUE));
  private static final WorkerExecutor DEFAULT_WORKER =
    VERTX.createSharedWorkerExecutor("fitnesse-worker", Math.max(4, Runtime.getRuntime().availableProcessors()));

  private VertxWorkerPool() {
  }

  public static Vertx vertx() {
    return VERTX;
  }

  public static WorkerExecutor worker() {
    return DEFAULT_WORKER;
  }

  public static ExecutorService newExecutor(String name, int maxWorkers) {
    int poolSize = Math.max(1, maxWorkers);
    WorkerExecutor executor = VERTX.createSharedWorkerExecutor(name, poolSize);
    return new VertxWorkerExecutorService(executor);
  }

  public static ExecutorService sharedExecutorService() {
    return new VertxWorkerExecutorService(worker());
  }

  public static CompletableFuture<Void> run(Runnable task) {
    return submit(() -> {
      task.run();
      return null;
    }, true);
  }

  public static CompletableFuture<Void> runDaemon(Runnable task) {
    return submit(() -> {
      task.run();
      return null;
    }, false);
  }

  public static CompletableFuture<Void> runDedicated(String name, Runnable task) {
    WorkerExecutor executor = VERTX.createSharedWorkerExecutor(name, 1);
    CompletableFuture<Void> promise = new CompletableFuture<>();
    executor.executeBlocking(() -> {
      task.run();
      return null;
    }, false).onComplete(ar -> {
      executor.close();
      if (ar.succeeded()) {
        promise.complete(null);
      } else {
        promise.completeExceptionally(ar.cause());
      }
    });
    return promise;
  }

  public static <T> CompletableFuture<T> submit(Callable<T> task) {
    return submit(task, true);
  }

  public static <T> CompletableFuture<T> submitDaemon(Callable<T> task) {
    return submit(task, false);
  }

  private static <T> CompletableFuture<T> submit(Callable<T> task, boolean trackContext) {
    Context context = trackContext ? Vertx.currentContext() : null;
    CompletableFuture<T> promise = new CompletableFuture<>();
    io.vertx.core.Future<T> future = worker().executeBlocking(task, false);
    future.onComplete(ar -> {
      Runnable complete = () -> {
        if (ar.succeeded()) {
          promise.complete(ar.result());
        } else {
          promise.completeExceptionally(ar.cause());
        }
      };
      if (context != null) {
        context.runOnContext(ignored -> complete.run());
      } else {
        complete.run();
      }
    });
    return promise;
  }

  private static final class VertxWorkerExecutorService extends AbstractExecutorService {
    private final WorkerExecutor executor;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private VertxWorkerExecutorService(WorkerExecutor executor) {
      this.executor = executor;
    }

    @Override
    public void shutdown() {
      if (shutdown.compareAndSet(false, true)) {
        executor.close();
      }
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown();
      return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return shutdown.get();
    }

    @Override
    public boolean isTerminated() {
      return shutdown.get();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return shutdown.get();
    }

    @Override
    public void execute(Runnable command) {
      submit(() -> {
        command.run();
        return null;
      });
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return VertxWorkerPool.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return submit(() -> {
        task.run();
        return result;
      });
    }

    @Override
    public Future<?> submit(Runnable task) {
      return submit(() -> {
        task.run();
        return null;
      });
    }
  }
}
