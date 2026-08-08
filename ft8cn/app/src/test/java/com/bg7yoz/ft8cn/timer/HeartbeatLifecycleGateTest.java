package com.bg7yoz.ft8cn.timer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class HeartbeatLifecycleGateTest {
    @Test
    public void currentEpochRunsTheHeartbeatPublication() {
        HeartbeatLifecycleGate gate = new HeartbeatLifecycleGate();
        long epoch = gate.currentEpoch();
        int[] publications = {0};

        assertTrue(gate.runIfCurrent(epoch, () -> publications[0]++));
        assertEquals(1, publications[0]);
    }

    @Test
    public void queuedHeartbeatRejectedAfterClearAndDoesNotPublish() throws Exception {
        HeartbeatLifecycleGate gate = new HeartbeatLifecycleGate();
        long epoch = gate.currentEpoch();
        CountDownLatch tokenChecked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        int[] publications = {0};
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> heartbeat = executor.submit(() -> {
            tokenChecked.countDown();
            if (!release.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("heartbeat was not released");
            }
            return gate.runIfCurrent(epoch, () -> publications[0]++);
        });

        try {
            assertTrue(tokenChecked.await(2, TimeUnit.SECONDS));
            gate.close();
            release.countDown();
            assertFalse(heartbeat.get(2, TimeUnit.SECONDS));
            assertEquals(0, publications[0]);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
