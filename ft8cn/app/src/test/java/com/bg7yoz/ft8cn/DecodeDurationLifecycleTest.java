package com.bg7yoz.ft8cn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bg7yoz.ft8cn.ft8listener.OnFt8Listen;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/** Proves that decode duration uses the same lifecycle admission as other effects. */
public class DecodeDurationLifecycleTest {
    @Test
    public void stopAfterTokenCheckRejectsThePendingDurationPublication() throws Exception {
        DecodeLifecycleGate gate = new DecodeLifecycleGate();
        assertTrue(gate.begin(7));
        CountDownLatch tokenChecked = new CountDownLatch(1);
        CountDownLatch releasePublication = new CountDownLatch(1);
        long[] duration = {0};
        OnFt8Listen listener = new OnFt8Listen() {
            @Override
            public boolean onDecodeDuration(long epoch, long value, Runnable publication) {
                return gate.runIfCurrent(epoch, publication);
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> decode = executor.submit(() -> {
            if (!gate.isCurrent(7)) {
                return false;
            }
            tokenChecked.countDown();
            if (!releasePublication.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("duration publication was not released");
            }
            return listener.onDecodeDuration(7, 321, () -> duration[0] = 321);
        });

        try {
            assertTrue("decode did not reach the cancellation check",
                    tokenChecked.await(2, TimeUnit.SECONDS));
            gate.close();
            releasePublication.countDown();
            assertFalse("stale duration publication was admitted", decode.get(2, TimeUnit.SECONDS));
            assertEquals("stale duration changed the UI value", 0, duration[0]);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void currentEpochAdmitsAndPublishesDecodeDuration() {
        DecodeLifecycleGate gate = new DecodeLifecycleGate();
        assertTrue(gate.begin(11));
        long[] duration = {0};
        OnFt8Listen listener = new OnFt8Listen() {
            @Override
            public boolean onDecodeDuration(long epoch, long value, Runnable publication) {
                return gate.runIfCurrent(epoch, publication);
            }
        };

        assertTrue(listener.onDecodeDuration(11, 654,
                () -> duration[0] = 654));
        assertEquals(654, duration[0]);
    }
}
