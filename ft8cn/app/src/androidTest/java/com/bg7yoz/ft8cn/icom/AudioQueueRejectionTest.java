package com.bg7yoz.ft8cn.icom;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bg7yoz.ft8cn.util.BoundedSerialExecutor;
import com.bg7yoz.ft8cn.util.SubmissionResult;

import static org.junit.Assert.assertEquals;
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
            assertEquals(SubmissionResult.SESSION_INACTIVE, audio.sendTxAudioData(new float[240]));
            assertTrue(audio.getDroppedTxAudioCount() >= 1);

            audio.isPttOn = true;
            audio.startTxAudio();
            assertTrue(audio.firstPacket.await(1, TimeUnit.SECONDS));
            SubmissionResult last = SubmissionResult.ENQUEUED;
            for (int i = 0; i < 10; i++) {
                last = audio.sendTxAudioData(new float[240]);
            }
            assertEquals(SubmissionResult.REJECTED, last);
            assertTrue("a full bounded queue must be accounted for instead of thrown",
                    audio.getDroppedTxAudioCount() >= 2);
        } finally {
            audio.releasePacket.countDown();
            audio.stopTXAudio();
        }
    }

    @Test
    public void icomUdpAndTrackedProtocolDoNotAdvanceAfterRejectedSubmit() throws Exception {
        BoundedSerialExecutor queue = new BoundedSerialExecutor(1);
        IcomUdpClient client = new IcomUdpClient(-1, queue);
        client.setActivated(true);
        try {
            queue.shutdown();
            assertEquals(SubmissionResult.REJECTED,
                    client.sendData(new byte[]{1}, "127.0.0.1", 9));

            IcomUdpBase protocol = new IcomUdpBase();
            protocol.udpClient = client;
            protocol.rigIp = "127.0.0.1";
            protocol.rigPort = 9;
            short before = protocol.trackedSeq;
            long lastSent = protocol.lastSentTime;
            assertEquals(SubmissionResult.REJECTED, protocol.sendTrackedPacket(new byte[16]));
            assertEquals(before, protocol.trackedSeq);
            assertEquals(lastSent, protocol.lastSentTime);
            assertEquals(0, protocol.txSeqBuffer.entries.size());
        } finally {
            client.setActivated(false);
        }
    }

    @Test
    public void icomAudioReturnsRejectedWhenSubmissionQueueIsClosed() {
        BoundedSerialExecutor queue = new BoundedSerialExecutor(1);
        IcomAudioUdp audio = new IcomAudioUdp(queue);
        audio.isPttOn = true;
        queue.shutdown();
        assertEquals(SubmissionResult.REJECTED, audio.sendTxAudioData(new float[240]));
    }

    private static final class SlowXieGuAudio extends XieGuAudioUdp {
        final CountDownLatch firstPacket = new CountDownLatch(1);
        final CountDownLatch releasePacket = new CountDownLatch(1);

        @Override
        public synchronized SubmissionResult sendTrackedPacket(byte[] data) {
            firstPacket.countDown();
            try {
                releasePacket.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return SubmissionResult.ENQUEUED;
        }
    }
}
