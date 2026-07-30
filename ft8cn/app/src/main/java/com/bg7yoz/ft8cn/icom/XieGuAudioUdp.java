package com.bg7yoz.ft8cn.icom;

/** 协谷音频流发送器：每个发射会话由一个单消费者快照队列驱动。 */

import android.util.Log;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.util.BoundedSerialExecutor;

import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

public class XieGuAudioUdp extends AudioUdp {
    private static final String TAG = "XieGuAudioUdp";
    private final BoundedSerialExecutor doTXThreadPool = new BoundedSerialExecutor(1);
    private final Object sessionLock = new Object();
    private AudioRunnable audioRunnable;
    private long generation;
    private volatile boolean audioIsRunning;

    @Override
    public void sendTxAudioData(float[] audioData) {
        if (audioData == null) return;

        byte[] snapshot = new byte[audioData.length * 2];
        for (int i = 0; i < audioData.length; i++) {
            float x = Math.max(-0.999999f, Math.min(0.999999f, audioData[i]));
            short sample = (short) (x * 32767.0f * GeneralVariables.volumePercent);
            System.arraycopy(IComPacketTypes.shortToBigEndian(sample), 0,
                    snapshot, i * 2, 2);
        }

        AudioRunnable session;
        synchronized (sessionLock) {
            session = audioRunnable;
            if (!audioIsRunning || session == null) {
                throw new RejectedExecutionException("audio session is not running");
            }
        }
        session.enqueue(snapshot);
    }

    @Override
    public void startTxAudio() {
        synchronized (sessionLock) {
            if (audioIsRunning) return;
            generation++;
            AudioRunnable session = new AudioRunnable(this, generation);
            audioRunnable = session;
            audioIsRunning = true;
            doTXThreadPool.cancelPending();
            doTXThreadPool.execute(session);
        }
    }

    @Override
    public void stopTXAudio() {
        AudioRunnable session;
        synchronized (sessionLock) {
            if (!audioIsRunning && audioRunnable == null) return;
            audioIsRunning = false;
            generation++;
            session = audioRunnable;
            audioRunnable = null;
        }
        doTXThreadPool.cancelPending();
        if (session != null) session.stop();
    }

    private boolean isCurrentSession(AudioRunnable session, long sessionGeneration) {
        synchronized (sessionLock) {
            return audioIsRunning && audioRunnable == session && generation == sessionGeneration;
        }
    }

    static final class AudioRunnable implements Runnable {
        private static final int QUEUE_CAPACITY = 8;
        private final int partialLen = (int) (IComPacketTypes.AUDIO_SAMPLE_RATE * 0.02);
        private final XieGuAudioUdp audioUdp;
        private final long generation;
        private final ArrayBlockingQueue<byte[]> snapshots = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        private volatile boolean running = true;

        AudioRunnable(XieGuAudioUdp audioUdp, long generation) {
            this.audioUdp = audioUdp;
            this.generation = generation;
            Log.d(TAG, "AudioRunnable: create session " + generation);
        }

        void enqueue(byte[] snapshot) {
            if (!snapshots.offer(Arrays.copyOf(snapshot, snapshot.length))) {
                throw new RejectedExecutionException("audio snapshot queue is full");
            }
        }

        @Override
        public void run() {
            byte[] current = null;
            int index = 0;
            byte[] audioPacket = new byte[partialLen * 2];
            long nextSendTime = System.currentTimeMillis();
            while (running && audioUdp.isCurrentSession(this, generation)) {
                if (current == null || index >= current.length) {
                    current = snapshots.poll();
                    index = 0;
                }
                Arrays.fill(audioPacket, (byte) 0);
                if (audioUdp.isPttOn && current != null) {
                    int copyLength = Math.min(audioPacket.length, current.length - index);
                    System.arraycopy(current, index, audioPacket, 0, copyLength);
                    index += copyLength;
                }

                audioUdp.sendTrackedPacket(IComPacketTypes.AudioPacket.getTxAudioPacket(
                        audioPacket, (short) 0, audioUdp.localId, audioUdp.remoteId,
                        audioUdp.innerSeq));
                audioUdp.innerSeq++;
                nextSendTime += 20;
                long sleepMs = nextSendTime - System.currentTimeMillis();
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else if (sleepMs < -1000) {
                    nextSendTime = System.currentTimeMillis();
                }
            }
        }

        void stop() {
            running = false;
            snapshots.clear();
        }
    }

    @Override
    public void onDataReceived(DatagramPacket packet, byte[] data) {
        super.onDataReceived(packet, data);
        if (IComPacketTypes.CONTROL_SIZE == data.length
                && IComPacketTypes.ControlPacket.getType(data) == IComPacketTypes.CMD_I_AM_READY) {
            startTxAudio();
        }
        if (!IComPacketTypes.AudioPacket.isAudioPacket(data)) return;
        byte[] audioData = IComPacketTypes.AudioPacket.getAudioData(data);
        if (onStreamEvents != null) onStreamEvents.OnReceivedAudioData(audioData);
    }
}
