package com.bg7yoz.ft8cn.ft8transmit;

/**
 * Owns the PTT/SCO transition around one transmit lifecycle.
 * A successful PTT OFF clears ownership so a later safety fallback cannot duplicate it.
 */
public final class TransmitPttCoordinator {
    public interface PttTarget {
        void setPtt(boolean on);
    }

    private boolean pttOn;
    private boolean scoRestorePending;
    private PttTarget activePttTarget;

    public synchronized void beforeTransmit(PttTarget target, boolean needSco, Runnable stopSco) {
        if (needSco) {
            scoRestorePending = true;
            if (stopSco != null) {
                stopSco.run();
            }
        }
        if (target != null) {
            // Keep the exact target that accepted PTT ON.  The ViewModel may replace
            // baseRig while a disconnect/reconnect is in flight; PTT OFF must never
            // be sent to that replacement rig.
            pttOn = true;
            activePttTarget = target;
            target.setPtt(true);
        }
    }

    public synchronized void afterTransmit(Runnable startSco) {
        RuntimeException failure = null;
        if (pttOn && activePttTarget != null) {
            try {
                activePttTarget.setPtt(false);
                pttOn = false;
                activePttTarget = null;
            } catch (RuntimeException e) {
                failure = e;
            }
        }
        if (failure == null && scoRestorePending && startSco != null) {
            try {
                startSco.run();
                scoRestorePending = false;
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public synchronized void safeOff(Runnable startSco) {
        RuntimeException failure = null;
        if (pttOn && activePttTarget != null) {
            try {
                activePttTarget.setPtt(false);
                pttOn = false;
                activePttTarget = null;
            } catch (RuntimeException e) {
                failure = e;
            }
        }
        if (failure == null && scoRestorePending && startSco != null) {
            try {
                startSco.run();
                scoRestorePending = false;
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    boolean isPttOnForTest() {
        return pttOn;
    }

    boolean isScoRestorePendingForTest() {
        return scoRestorePending;
    }
}
