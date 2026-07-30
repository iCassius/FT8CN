package com.bg7yoz.ft8cn.ft8transmit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TransmitPttCoordinatorTest {
    @Test
    public void activeTransmitTurnsPttOffAndRestoresScoOnce() {
        TransmitPttCoordinator coordinator = new TransmitPttCoordinator();
        FakeRig rig = new FakeRig();

        coordinator.beforeTransmit(rig, true, rig::stopSco);
        coordinator.afterTransmit(rig::startSco);
        coordinator.safeOff(rig::startSco);

        assertEquals(2, rig.pttCalls.size());
        assertTrue(rig.pttCalls.get(0));
        assertFalse(rig.pttCalls.get(1));
        assertEquals(1, rig.stopScoCalls);
        assertEquals(1, rig.startScoCalls);
        assertFalse(coordinator.isPttOnForTest());
        assertFalse(coordinator.isScoRestorePendingForTest());
    }

    @Test
    public void idleFallbackDoesNotSendPttOffOrTouchSco() {
        TransmitPttCoordinator coordinator = new TransmitPttCoordinator();
        FakeRig rig = new FakeRig();

        coordinator.afterTransmit(rig::startSco);
        coordinator.safeOff(rig::startSco);

        assertEquals(0, rig.pttCalls.size());
        assertEquals(0, rig.stopScoCalls);
        assertEquals(0, rig.startScoCalls);
    }

    @Test
    public void failedPttOffRemainsOwnedForSafeFallback() {
        TransmitPttCoordinator coordinator = new TransmitPttCoordinator();
        FakeRig rig = new FakeRig();
        rig.failPttOffOnce = true;

        coordinator.beforeTransmit(rig, true, rig::stopSco);
        try {
            coordinator.afterTransmit(rig::startSco);
        } catch (RuntimeException expected) {
            // MainViewModel.onCleared invokes safeOff in its finally path.
        }
        assertEquals(0, rig.startScoCalls);
        coordinator.safeOff(rig::startSco);

        assertEquals(3, rig.pttCalls.size());
        assertTrue(rig.pttCalls.get(0));
        assertFalse(rig.pttCalls.get(1));
        assertFalse(rig.pttCalls.get(2));
        assertEquals(1, rig.stopScoCalls);
        assertEquals(1, rig.startScoCalls);
        assertFalse(coordinator.isPttOnForTest());
        assertFalse(coordinator.isScoRestorePendingForTest());
    }

    @Test
    public void terminationUsesTheRigThatAcceptedPttOn() {
        TransmitPttCoordinator coordinator = new TransmitPttCoordinator();
        FakeRig originalRig = new FakeRig();
        FakeRig replacementRig = new FakeRig();

        coordinator.beforeTransmit(originalRig, false, null);
        coordinator.afterTransmit(replacementRig::startSco);

        assertEquals(2, originalRig.pttCalls.size());
        assertTrue(originalRig.pttCalls.get(0));
        assertFalse(originalRig.pttCalls.get(1));
        assertEquals(0, replacementRig.pttCalls.size());
    }

    private static final class FakeRig implements TransmitPttCoordinator.PttTarget {
        private final List<Boolean> pttCalls = new ArrayList<>();
        private int stopScoCalls;
        private int startScoCalls;
        private boolean failPttOffOnce;

        @Override
        public void setPtt(boolean on) {
            pttCalls.add(on);
            if (!on && failPttOffOnce) {
                failPttOffOnce = false;
                throw new IllegalStateException("simulated PTT OFF failure");
            }
        }

        private void stopSco() {
            stopScoCalls++;
        }

        private void startSco() {
            startScoCalls++;
        }
    }
}
