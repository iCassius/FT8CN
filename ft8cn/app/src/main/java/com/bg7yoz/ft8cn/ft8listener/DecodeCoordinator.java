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

    private static final class ActiveRun {
        private Future<?> future;
        private boolean started;
    }

    private final Object lock = new Object();
    private final ExecutorService executor;
    private final Runnable afterTerminalCleanup;
    private long epoch;
    private boolean active;
    private boolean stopped;
    private Future<?> activeFuture;
    private ActiveRun activeRun;

    DecodeCoordinator(String threadName) {
        this(threadName, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        }), () -> { });
    }

    DecodeCoordinator(String threadName, ExecutorService executor) {
        this(threadName, executor, () -> { });
    }

    DecodeCoordinator(String threadName, ExecutorService executor, Runnable afterTerminalCleanup) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.afterTerminalCleanup = Objects.requireNonNull(afterTerminalCleanup,
                "afterTerminalCleanup");
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
            ActiveRun run = new ActiveRun();
            activeRun = run;
            try {
                run.future = executor.submit(() -> runTask(task, listener, token, run));
                activeFuture = run.future;
                return true;
            } catch (RuntimeException rejected) {
                clearRunLocked(run);
                return false;
            }
        }
    }

    void cancelActive() {
        synchronized (lock) {
            epoch++;
            ActiveRun run = activeRun;
            if (run != null) {
                if (run.future != null) {
                    run.future.cancel(true);
                }
                // A Future cancelled before runTask acquires the lock never
                // reaches runTask's finally block, so release that slot here.
                // A started task must retain the slot until its own finally.
                if (!run.started) {
                    clearRunLocked(run);
                }
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
            ActiveRun run = activeRun;
            if (run != null) {
                if (run.future != null) {
                    run.future.cancel(true);
                }
                if (!run.started) {
                    clearRunLocked(run);
                }
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

    private void runTask(DecodeTask task, Listener listener, DecodeToken token, ActiveRun run) {
        boolean started;
        boolean cancelled = false;
        Throwable failure = null;

        synchronized (lock) {
            if (activeRun != run || stopped) {
                return;
            }
            run.started = true;
            started = true;
        }

        try {
            token.throwIfCancelled();
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
                // The decode body has terminated before the terminal
                // callback. Release admission for the next decode now, but
                // retain the ActiveRun identity until this old callback and
                // its finally path have returned. A late old cleanup must
                // therefore never clear a newly accepted run.
                synchronized (lock) {
                    if (activeRun == run) {
                        active = false;
                        activeFuture = null;
                    }
                }
                try {
                    listener.onFinished(token.epoch(), cancelled, failure);
                } catch (Throwable ignored) {
                    // Terminal cleanup must not keep the coordinator occupied.
                }
            }
            synchronized (lock) {
                clearRunLocked(run);
            }
            try {
                afterTerminalCleanup.run();
            } catch (Throwable ignored) {
                // Test/diagnostic observers must not change terminal cleanup.
            }
        }
    }

    private void clearRunLocked(ActiveRun run) {
        if (activeRun == run) {
            active = false;
            activeFuture = null;
            activeRun = null;
        }
    }
}
