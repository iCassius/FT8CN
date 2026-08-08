package com.bg7yoz.ft8cn.wave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

/** Deterministic monitor-generation and terminal-state coverage. */
@RunWith(AndroidJUnit4.class)
public class HamRecorderSessionLifecycleTest {
    @Test
    public void oldMonitorCannotReceiveAudioOrForceCompleteAfterStopAndRestart() throws Exception {
        ManualMicRecorder mic = new ManualMicRecorder();
        HamRecorder recorder = new HamRecorder(null, mic);
        AtomicInteger oldCallbacks = new AtomicInteger();
        AtomicInteger newCallbacks = new AtomicInteger();

        recorder.startRecord();
        long oldSession = mic.lastSessionId;
        mic.emitRunning(oldSession, true);
        HamRecorder.VoiceDataMonitor oldMonitor = recorder.getVoiceData(1, true,
                data -> oldCallbacks.incrementAndGet());
        assertTrue(oldMonitor != null);

        CountDownLatch getReady = new CountDownLatch(1);
        CountDownLatch allowGet = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<HamRecorder.VoiceDataMonitor> concurrentGet = executor.submit(() -> {
            getReady.countDown();
            if (!allowGet.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("getVoiceData was not released");
            }
            return recorder.getVoiceData(1, true, data -> { });
        });
        try {
            assertTrue(getReady.await(2, TimeUnit.SECONDS));
            recorder.stopRecord();
            allowGet.countDown();
            assertNull("getVoiceData admitted after stop", concurrentGet.get(2, TimeUnit.SECONDS));

            oldMonitor.forceComplete();
            assertEquals(0, oldCallbacks.get());

            recorder.startRecord();
            long newSession = mic.lastSessionId;
            assertTrue(newSession > oldSession);
            mic.emitRunning(newSession, true);
            HamRecorder.VoiceDataMonitor newMonitor = recorder.getVoiceData(1, true,
                    data -> newCallbacks.incrementAndGet());
            assertTrue(newMonitor != null);

            mic.emitData(oldSession, filled(12, 1f));
            assertEquals("old session audio reached the new monitor", 0, newCallbacks.get());
            mic.emitData(newSession, filled(12, 2f));
            assertEquals(1, newCallbacks.get());
        } finally {
            allowGet.countDown();
            recorder.onCleared();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void monitorCallbackExceptionIsPropagatedAndMonitorBecomesTerminal() {
        ManualMicRecorder mic = new ManualMicRecorder();
        HamRecorder recorder = new HamRecorder(null, mic);
        recorder.startRecord();
        long session = mic.lastSessionId;
        mic.emitRunning(session, true);
        IllegalStateException expected = new IllegalStateException("decode failed");
        HamRecorder.VoiceDataMonitor monitor = recorder.getVoiceData(1, true,
                data -> { throw expected; });

        boolean propagated = false;
        try {
            mic.emitData(session, filled(12, 3f));
        } catch (IllegalStateException actual) {
            assertSame(expected, actual);
            propagated = true;
        }
        assertTrue("monitor callback exception was swallowed", propagated);
        mic.emitData(session, filled(12, 4f));
        assertEquals(0, recorder.getVoiceMonitorCount());
        recorder.onCleared();
    }

    private static float[] filled(int count, float value) {
        float[] data = new float[count];
        java.util.Arrays.fill(data, value);
        return data;
    }

    private static final class ManualMicRecorder extends MicRecorder {
        private OnDataListener dataListener;
        private OnStateListener stateListener;
        long lastSessionId;

        ManualMicRecorder() {
            super(new AudioRecordFactory() {
                @Override
                public int getMinBufferSize() {
                    return 2;
                }

                @Override
                public AudioRecordHandle create(int bufferSize) {
                    return null;
                }
            }, new ForegroundServiceController() {
                @Override
                public boolean start(long sessionId) {
                    return true;
                }

                @Override
                public void stop(long sessionId) {
                }
            });
        }

        @Override
        public void setOnDataListener(OnDataListener listener) {
            dataListener = listener;
        }

        @Override
        void setOnStateListener(OnStateListener listener) {
            stateListener = listener;
        }

        @Override
        long startSession() {
            lastSessionId++;
            return lastSessionId;
        }

        @Override
        void stopSession(long sessionId) {
        }

        void emitRunning(long sessionId, boolean running) {
            stateListener.onStateChanged(sessionId, running);
        }

        void emitData(long sessionId, float[] data) {
            dataListener.onDataReceived(sessionId, data, data.length);
        }
    }
}
