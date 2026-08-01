package com.bg7yoz.ft8cn.icom;

/** ICOM audio stream sender. */

import android.util.Log;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.util.BoundedSerialExecutor;

import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public class IcomAudioUdp extends AudioUdp {
    private static final String TAG = "IcomAudioUdp";
    private final BoundedSerialExecutor doTXThreadPool = new BoundedSerialExecutor(4);
    private final AtomicLong txGeneration = new AtomicLong();
    private final AtomicLong droppedTxAudioCount = new AtomicLong();

    @Override
    public void sendTxAudioData(float[] audioData) {
        if (audioData == null) return;

        // Convert before enqueueing. The task owns this byte snapshot for its
        // entire 20 ms packet sequence; no later submit can replace it.
        byte[] snapshot = new byte[audioData.length * 2];
        for (int i = 0; i < audioData.length; i++) {
            float x = Math.max(-1.0f, Math.min(1.0f, audioData[i]));
            short sample = (short) (x * 32767.0f * GeneralVariables.volumePercent);
            System.arraycopy(IComPacketTypes.shortToBigEndian(sample), 0,
                    snapshot, i * 2, 2);
        }
        long generation = txGeneration.get();
        try {
            doTXThreadPool.submit(new DoTXAudioRunnable(this, snapshot, generation));
        } catch (RejectedExecutionException rejected) {
            long dropped = droppedTxAudioCount.incrementAndGet();
            Log.w(TAG, "ICOM TX audio queue rejected snapshot; dropped=" + dropped);
        }
    }

    @Override
    public void stopTXAudio() {
        txGeneration.incrementAndGet();
        doTXThreadPool.cancelPending();
        // The active task observes the generation fence and exits at its next
        // 20 ms boundary; keeping the executor alive permits a later restart.
    }

    private boolean isCurrentTx(long generation) {
        return txGeneration.get() == generation && isPttOn;
    }

    long getDroppedTxAudioCount() {
        return droppedTxAudioCount.get();
    }

    private static class DoTXAudioRunnable implements Runnable {
        private final IcomAudioUdp icomAudioUdp;
        private final byte[] audioData;
        private final long generation;

        DoTXAudioRunnable(IcomAudioUdp icomAudioUdp, byte[] audioData, long generation) {
            this.icomAudioUdp = icomAudioUdp;
            this.audioData = Arrays.copyOf(audioData, audioData.length);
            this.generation = generation;
        }

        @Override
        public void run() {
            final int packetBytes = IComPacketTypes.TX_BUFFER_SIZE * 2;
            byte[] audioPacket = new byte[packetBytes];
            long nextSendTime = System.currentTimeMillis();
            for (int packetIndex = 0;
                 packetIndex < (audioData.length / packetBytes) + 8;
                 packetIndex++) {
                if (!icomAudioUdp.isCurrentTx(generation)) break;

                icomAudioUdp.sendTrackedPacket(IComPacketTypes.AudioPacket.getTxAudioPacket(
                        audioPacket, (short) 0, icomAudioUdp.localId,
                        icomAudioUdp.remoteId, icomAudioUdp.innerSeq));
                icomAudioUdp.innerSeq++;

                Arrays.fill(audioPacket, (byte) 0);
                if (packetIndex >= 3) {
                    int offset = (packetIndex - 3) * packetBytes;
                    if (offset < audioData.length) {
                        System.arraycopy(audioData, offset, audioPacket, 0,
                                Math.min(packetBytes, audioData.length - offset));
                    }
                }
                nextSendTime += 20;
                long sleepMs = nextSendTime - System.currentTimeMillis();
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            Log.d(TAG, "run: 音频发送完毕！！");
        }
    }

    @Override
    public void onDataReceived(DatagramPacket packet, byte[] data) {
        super.onDataReceived(packet, data);
        if (!IComPacketTypes.AudioPacket.isAudioPacket(data)) return;
        byte[] audioData = IComPacketTypes.AudioPacket.getAudioData(data);
        if (onStreamEvents != null) onStreamEvents.OnReceivedAudioData(audioData);
    }
}
