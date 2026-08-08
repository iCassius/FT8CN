package com.bg7yoz.ft8cn;

import java.util.Objects;

/**
 * Linearizes decode-result side-effect admission with lifecycle shutdown.
 *
 * <p>The lock is held only while deciding whether an effect is admitted. The
 * effect itself runs after the lock is released, so database, UI, network and
 * radio calls cannot hold this gate or create a lock-order dependency.</p>
 */
final class DecodeLifecycleGate {
    private final Object lock = new Object();
    private long currentEpoch = -1;
    private boolean closed;

    boolean begin(long epoch) {
        synchronized (lock) {
            if (closed) {
                return false;
            }
            currentEpoch = epoch;
            return true;
        }
    }

    boolean isCurrent(long epoch) {
        synchronized (lock) {
            return !closed && currentEpoch == epoch;
        }
    }

    /**
     * Admit one effect atomically with respect to {@link #close()}.
     *
     * <p>If this returns true, the effect owns a linearization point before a
     * concurrent close. If close wins the race, the effect is not invoked.</p>
     */
    boolean runIfCurrent(long epoch, Runnable effect) {
        Objects.requireNonNull(effect, "effect");
        synchronized (lock) {
            if (closed || currentEpoch != epoch) {
                return false;
            }
        }
        effect.run();
        return true;
    }

    void close() {
        synchronized (lock) {
            closed = true;
            currentEpoch++;
        }
    }
}
