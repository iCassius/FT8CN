package com.bg7yoz.ft8cn.util;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A single-consumer executor with bounded, interruptible backpressure.
 *
 * <p>Normal submission is deliberately fail-fast when the queue is full.
 * Callers that can wait may use the timed overload. Neither path waits while
 * holding a client/session lock.</p>
 */
public final class BoundedSerialExecutor implements Executor {
    private static final long KEEP_ALIVE_SECONDS = 1L;

    private final ThreadPoolExecutor executor;

    public BoundedSerialExecutor(int queueCapacity) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        executor = new ThreadPoolExecutor(
                1,
                1,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
    }

    /**
     * Submit without waiting. A full or shut down executor is reported to the
     * caller instead of silently dropping the task.
     */
    public boolean submit(Runnable command) {
        Objects.requireNonNull(command, "command");
        executor.execute(command);
        return true;
    }

    /**
     * Submit with an interruptible, bounded wait for queue capacity.
     */
    public boolean submit(Runnable command, long timeout, TimeUnit unit)
            throws InterruptedException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(unit, "unit");
        try {
            executor.execute(command);
            return true;
        } catch (RejectedExecutionException rejected) {
            if (executor.isShutdown()) {
                throw rejected;
            }
            if (!executor.getQueue().offer(command, timeout, unit)) {
                throw new RejectedExecutionException("bounded queue is full", rejected);
            }
            if (executor.isShutdown() && executor.getQueue().remove(command)) {
                throw new RejectedExecutionException("executor shut down while submitting", rejected);
            }
            return true;
        }
    }

    @Override
    public void execute(Runnable command) {
        submit(command);
    }

    /** Cancel tasks that have not started. The executor remains reusable. */
    public void cancelPending() {
        executor.getQueue().clear();
    }

    /** Idempotently stop accepting work and interrupt the active task. */
    public void shutdown() {
        executor.shutdownNow();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }
}
