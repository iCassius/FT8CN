package com.bg7yoz.ft8cn.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A single-consumer executor with bounded backpressure.
 *
 * <p>Network protocol writers use this instead of a cached executor so that
 * submitted snapshots are sent in submission order without allowing an
 * unbounded backlog to accumulate.</p>
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
                (task, pool) -> {
                    if (pool.isShutdown()) {
                        throw new RejectedExecutionException("executor is shut down");
                    }
                    boolean interrupted = false;
                    try {
                        for (;;) {
                            try {
                                pool.getQueue().put(task);
                                return;
                            } catch (InterruptedException e) {
                                interrupted = true;
                            }
                        }
                    } finally {
                        if (interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
        executor.allowCoreThreadTimeOut(true);
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        executor.execute(command);
    }
}
