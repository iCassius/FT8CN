package com.bg7yoz.ft8cn.ft8listener;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Owns the single decode slot and invalidates work when its lifecycle ends.
 * A cancelled native call may take time to return, so a new task is not
 * admitted until the old task has actually left this coordinator.
 */
final class DecodeCoordinator {
    interface DecodeTask {
        void run(DecodeToken token) throws Exception;
    }

    interface Listener {
        void onStarted(long epoch);

        void onFinished(long epoch, boolean cancelled, Throwable failure);
    }

    static final class DecodeToken {
        private final DecodeCoordinator owner;
        private final long epoch;

        private DecodeToken(DecodeCoordinator owner, long epoch) {
            this.owner = owner;
            this.epoch = epoch;
        }

        long epoch() {
            return epoch;
        }

        boolean isCurrent() {
            return owner.isCurrent(epoch);
        }

        void throwIfCancelled() {
            if (!isCurrent() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("decode epoch is no longer current");
            }
        }
    }

    private final Object lock = new Object();
    private final ExecutorService executor;
    private long epoch;
    private boolean active;
    private boolean stopped;
    private Future<?> activeFuture;

    DecodeCoordinator(String threadName) {
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    boolean submit(DecodeTask task, Listener listener) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(listener, "listener");

        synchronized (lock) {
            if (stopped || active) {
                return false;
            }

            active = true;
            long taskEpoch = ++epoch;
            DecodeToken token = new DecodeToken(this, taskEpoch);
            try {
                activeFuture = executor.submit(() -> runTask(task, listener, token));
                return true;
            } catch (RuntimeException rejected) {
                active = false;
                activeFuture = null;
                return false;
            }
        }
    }

    void cancelActive() {
        synchronized (lock) {
            epoch++;
            if (activeFuture != null) {
                activeFuture.cancel(true);
            }
        }
    }

    void stop() {
        synchronized (lock) {
            if (stopped) {
                return;
            }
            stopped = true;
            epoch++;
            if (activeFuture != null) {
                activeFuture.cancel(true);
            }
        }
        executor.shutdownNow();
    }

    boolean isActive() {
        synchronized (lock) {
            return active;
        }
    }

    private boolean isCurrent(long taskEpoch) {
        synchronized (lock) {
            return !stopped && epoch == taskEpoch;
        }
    }

    private void runTask(DecodeTask task, Listener listener, DecodeToken token) {
        boolean started = false;
        boolean cancelled = false;
        Throwable failure = null;
        try {
            token.throwIfCancelled();
            started = true;
            listener.onStarted(token.epoch());
            token.throwIfCancelled();
            task.run(token);
        } catch (Throwable error) {
            if (error instanceof CancellationException
                    || error instanceof InterruptedException
                    || !token.isCurrent()) {
                cancelled = true;
            } else {
                failure = error;
            }
        } finally {
            if (started) {
                try {
                    listener.onFinished(token.epoch(), cancelled, failure);
                } catch (Throwable ignored) {
                    // Terminal cleanup must not keep the coordinator occupied.
                }
            }
            synchronized (lock) {
                active = false;
                activeFuture = null;
            }
        }
    }
}
