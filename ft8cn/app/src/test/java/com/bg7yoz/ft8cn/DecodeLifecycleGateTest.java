package com.bg7yoz.ft8cn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class DecodeLifecycleGateTest {
    @Test
    public void clearAfterEntryCheckRejectsTheStaleEffect() throws Exception {
        DecodeLifecycleGate gate = new DecodeLifecycleGate();
        assertTrue(gate.begin(41));

        CountDownLatch entryChecked = new CountDownLatch(1);
        CountDownLatch allowAdmission = new CountDownLatch(1);
        int[] sideEffects = {0};
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> staleDecode = executor.submit(() -> {
            // This is the vulnerable interleave from afterDecode: the old
            // implementation stopped here and then performed side effects.
            if (!gate.isCurrent(41)) return;
            entryChecked.countDown();
            try {
                if (!allowAdmission.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("duration admission was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("duration admission worker was interrupted", interrupted);
            }
            gate.runIfCurrent(41, () -> sideEffects[0]++);
        });

        try {
            assertTrue("decode did not reach the entry check", entryChecked.await(2, TimeUnit.SECONDS));
            gate.close();
            allowAdmission.countDown();
            staleDecode.get(2, TimeUnit.SECONDS);
            assertTrue("decode worker did not terminate", staleDecode.isDone());
        } finally {
            executor.shutdownNow();
        }

        assertEquals("stale decode admitted a side effect after close", 0, sideEffects[0]);
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
