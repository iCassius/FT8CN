package com.bg7yoz.ft8cn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class DecodeLifecycleGateTest {
    @Test
    public void clearAfterEntryCheckRejectsTheStaleEffect() throws Exception {
        DecodeLifecycleGate gate = new DecodeLifecycleGate();
        assertTrue(gate.begin(41));

        CountDownLatch entryChecked = new CountDownLatch(1);
        CountDownLatch allowAdmission = new CountDownLatch(1);
        AtomicInteger sideEffects = new AtomicInteger();
        Thread staleDecode = new Thread(() -> {
            // This is the vulnerable interleave from afterDecode: the old
            // implementation stopped here and then performed side effects.
            if (!gate.isCurrent(41)) return;
            entryChecked.countDown();
            try {
                assertTrue(allowAdmission.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            gate.runIfCurrent(41, sideEffects::incrementAndGet);
        }, "decode-lifecycle-race-test");

        staleDecode.start();
        assertTrue("decode did not reach the entry check", entryChecked.await(2, TimeUnit.SECONDS));
        gate.close();
        allowAdmission.countDown();
        staleDecode.join(TimeUnit.SECONDS.toMillis(2));

        assertEquals("stale decode admitted a side effect after close", 0, sideEffects.get());
    }

    @Test
    public void aNewEpochRemainsCurrentUntilClosed() {
        DecodeLifecycleGate gate = new DecodeLifecycleGate();
        assertTrue(gate.begin(1));
        assertTrue(gate.begin(2));
        assertTrue(gate.isCurrent(2));
        assertTrue(!gate.isCurrent(1));
        assertTrue(gate.runIfCurrent(2, () -> { }));
        gate.close();
        assertTrue(!gate.runIfCurrent(2, () -> { }));
    }
}
