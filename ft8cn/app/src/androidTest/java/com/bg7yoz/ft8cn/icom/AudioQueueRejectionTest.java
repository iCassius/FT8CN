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
    public void civAndConnInfoSequencesDoNotAdvanceAfterRejectedSubmit() {
        IcomCivUdp civ = new IcomCivUdp();
        civ.udpClient = null;
        short civSequence = civ.civSeq;
        assertEquals(SubmissionResult.SESSION_INACTIVE, civ.sendOpenClose(true));
        assertEquals(civSequence, civ.civSeq);
        assertEquals(SubmissionResult.SESSION_INACTIVE, civ.sendCivData(new byte[]{1, 2}));
        assertEquals(civSequence, civ.civSeq);

        byte[] connInfo = new byte[IComPacketTypes.CONNINFO_SIZE];
        RejectingIcomControl icom = new RejectingIcomControl();
        short icomSequence = icom.innerSeq;
        icom.onReceiveConnInfoPacket(connInfo);
        assertEquals(icomSequence, icom.innerSeq);

        RejectingXieGuControl xieGu = new RejectingXieGuControl();
        short xieGuSequence = xieGu.innerSeq;
        xieGu.onReceiveConnInfoPacket(connInfo);
        assertEquals(xieGuSequence, xieGu.innerSeq);
    }

    @Test
    public void rejectedWifiPttKeepsCivAndAudioStateIncludingOff() {
        IComWifiRig icom = new IComWifiRig("127.0.0.1", 0, "user", "password");
        XieGuWifiRig xieGu = new XieGuWifiRig("127.0.0.1", 0, "user", "password");
        assertRejectedOffKeepsWifiPttState(icom);
        assertRejectedOffKeepsWifiPttState(xieGu);
    }

    private static void assertRejectedOffKeepsWifiPttState(WifiRig rig) {
        ControlUdp control = new ControlUdp("user", "password", "127.0.0.1", 0);
        control.civUdp = new RejectingCivUdp();
        control.audioUdp = new AudioUdp();
        rig.controlUdp = control;
        rig.isPttOn = true;
        control.audioUdp.isPttOn = true;

        assertEquals(SubmissionResult.REJECTED, rig.setPttOn(false));
        assertTrue(rig.isPttOn);
        assertTrue(control.audioUdp.isPttOn);
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

    private static final class RejectingCivUdp extends IcomCivUdp {
        @Override
        public SubmissionResult sendPttAction(boolean on) {
            return SubmissionResult.REJECTED;
        }
    }

    private static final class RejectingIcomControl extends IcomControlUdp {
        RejectingIcomControl() {
            super("user", "password", "127.0.0.1", 0, false);
        }

        @Override
        public synchronized SubmissionResult sendTrackedPacket(byte[] data) {
            return SubmissionResult.REJECTED;
        }
    }

    private static final class RejectingXieGuControl extends XieGuControlUdp {
        RejectingXieGuControl() {
            super("user", "password", "127.0.0.1", 0, false);
        }

        @Override
        public synchronized SubmissionResult sendTrackedPacket(byte[] data) {
            return SubmissionResult.REJECTED;
        }
    }
}
