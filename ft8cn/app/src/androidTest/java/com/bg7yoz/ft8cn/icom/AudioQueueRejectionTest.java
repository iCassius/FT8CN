package com.bg7yoz.ft8cn.icom;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class AudioQueueRejectionTest {
    @Test
    public void xieGuAudioDropsNotReadyAndFullQueueWithoutEscapingCallback() throws Exception {
        SlowXieGuAudio audio = new SlowXieGuAudio();
        try {
            // A transmission callback can arrive before the network audio stream is ready.
            audio.sendTxAudioData(new float[240]);
            assertTrue(audio.getDroppedTxAudioCount() >= 1);

            audio.isPttOn = true;
            audio.startTxAudio();
            assertTrue(audio.firstPacket.await(1, TimeUnit.SECONDS));
            for (int i = 0; i < 10; i++) {
                audio.sendTxAudioData(new float[240]);
            }
            assertTrue("a full bounded queue must be accounted for instead of thrown",
                    audio.getDroppedTxAudioCount() >= 2);
        } finally {
            audio.releasePacket.countDown();
            audio.stopTXAudio();
        }
    }

    private static final class SlowXieGuAudio extends XieGuAudioUdp {
        final CountDownLatch firstPacket = new CountDownLatch(1);
        final CountDownLatch releasePacket = new CountDownLatch(1);

        @Override
        public synchronized void sendTrackedPacket(byte[] data) {
            firstPacket.countDown();
            try {
                releasePacket.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
