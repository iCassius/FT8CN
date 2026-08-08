package com.bg7yoz.ft8cn.timer;

/**
 * Linearizes a queued heartbeat with its owner lifecycle. The guarded action
 * is intentionally tiny (LiveData postValue and state snapshots), so close
 * cannot race an admitted heartbeat into a cleared owner.
 */
public final class HeartbeatLifecycleGate {
    private final Object lock = new Object();
    private long epoch;
    private boolean closed;

    public long currentEpoch() {
        synchronized (lock) {
            return epoch;
        }
    }

    public boolean runIfCurrent(long expectedEpoch, Runnable action) {
        synchronized (lock) {
            if (closed || epoch != expectedEpoch) {
                return false;
            }
            action.run();
            return true;
        }
    }

    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            epoch++;
        }
    }
}
