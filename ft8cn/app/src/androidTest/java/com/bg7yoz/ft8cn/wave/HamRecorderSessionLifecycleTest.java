package com.bg7yoz.ft8cn.wave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.media.AudioRecord;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;

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

        CountDownLatch getEntered = new CountDownLatch(1);
        CountDownLatch releaseGet = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        recorder.setBeforeGetVoiceDataLockHookForTest(() -> {
            getEntered.countDown();
            try {
                if (!releaseGet.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("getVoiceData was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("getVoiceData barrier was interrupted", interrupted);
            }
        });
        Future<HamRecorder.VoiceDataMonitor> concurrentGet = executor.submit(
                () -> recorder.getVoiceData(1, true, data -> { }));
        try {
            assertTrue("getVoiceData did not enter while the old session was active",
                    getEntered.await(2, TimeUnit.SECONDS));
            recorder.stopRecord();
            releaseGet.countDown();
            assertNull("getVoiceData admitted after stop", concurrentGet.get(2, TimeUnit.SECONDS));
            recorder.setBeforeGetVoiceDataLockHookForTest(null);

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
            releaseGet.countDown();
            recorder.setBeforeGetVoiceDataLockHookForTest(null);
            recorder.onCleared();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void concurrentStopStartRetiresRealMicSessionBeforeNewStartIsAdmitted() throws Exception {
        ControlledAudioRecord oldRecord = new ControlledAudioRecord((short) 11);
        ControlledAudioRecord newRecord = new ControlledAudioRecord((short) 22);
        ControlledMicRecorder mic = new ControlledMicRecorder(
                new ControlledFactory(oldRecord, newRecord));
        HamRecorder recorder = new HamRecorder(null, mic);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> stop = null;
        Future<?> start = null;

        try {
            recorder.startRecord();
            assertTrue("old session did not enter its controlled read",
                    oldRecord.readStarted.await(2, TimeUnit.SECONDS));
            long oldSession = mic.lastSessionId;
            assertTrue(oldSession > 0);

            stop = executor.submit(recorder::stopRecord);
            assertTrue("stop did not enter MicRecorder retirement",
                    mic.retireEntered.await(2, TimeUnit.SECONDS));

            CountDownLatch startAttempted = new CountDownLatch(1);
            CountDownLatch startCompleted = new CountDownLatch(1);
            start = executor.submit(() -> {
                startAttempted.countDown();
                recorder.startRecord();
                startCompleted.countDown();
            });
            assertTrue(startAttempted.await(2, TimeUnit.SECONDS));
            assertFalse("start was admitted before the old Mic session was retired",
                    startCompleted.await(0, TimeUnit.NANOSECONDS));

            // This releases only the in-memory ownership barrier. AudioRecord
            // release happens later, outside HamRecorder.lifecycleLock.
            mic.allowRetire.countDown();
            stop.get(2, TimeUnit.SECONDS);
            start.get(2, TimeUnit.SECONDS);

            long newSession = mic.lastSessionId;
            assertTrue("start did not obtain a new Mic session identity",
                    newSession > oldSession);
            assertTrue("new Mic session was not actually running",
                    newRecord.readStarted.await(2, TimeUnit.SECONDS));
            assertTrue(recorder.isRunning());

            CountDownLatch delivered = new CountDownLatch(1);
            AtomicInteger sample = new AtomicInteger();
            assertTrue(recorder.getVoiceData(1, true, data -> {
                sample.set(Math.round(data[0] * 32768.0f));
                delivered.countDown();
            }) != null);
            newRecord.sampleReady.countDown();
            assertTrue("new session data was not delivered", delivered.await(2, TimeUnit.SECONDS));
            assertEquals(22, sample.get());
        } finally {
            mic.allowRetire.countDown();
            oldRecord.sampleReady.countDown();
            newRecord.sampleReady.countDown();
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

    /** Real MicRecorder with only the AudioRecord boundary controlled. */
    private static final class ControlledMicRecorder extends MicRecorder {
        final CountDownLatch retireEntered = new CountDownLatch(1);
        final CountDownLatch allowRetire = new CountDownLatch(1);
        volatile long lastSessionId;

        ControlledMicRecorder(AudioRecordFactory factory) {
            super(factory, new ForegroundServiceController() {
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
        long startSession() {
            long sessionId = super.startSession();
            lastSessionId = sessionId;
            return sessionId;
        }

        @Override
        RetiredSession retireSession(long sessionId) {
            retireEntered.countDown();
            try {
                if (!allowRetire.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Mic session retirement barrier timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Mic session retirement barrier interrupted", interrupted);
            }
            return super.retireSession(sessionId);
        }
    }

    private static final class ControlledFactory implements AudioRecordFactory {
        private final ArrayDeque<AudioRecordHandle> records = new ArrayDeque<>();

        ControlledFactory(AudioRecordHandle... records) {
            this.records.addAll(Arrays.asList(records));
        }

        @Override
        public int getMinBufferSize() {
            return 24;
        }

        @Override
        public synchronized AudioRecordHandle create(int bufferSize) {
            return records.removeFirst();
        }
    }

    private static final class ControlledAudioRecord implements AudioRecordHandle {
        final CountDownLatch readStarted = new CountDownLatch(1);
        final CountDownLatch sampleReady = new CountDownLatch(1);
        private final short sample;
        private volatile boolean started;
        private volatile boolean released;
        private boolean sampleReturned;

        ControlledAudioRecord(short sample) {
            this.sample = sample;
        }

        @Override
        public int getState() {
            return AudioRecord.STATE_INITIALIZED;
        }

        @Override
        public int getRecordingState() {
            return started && !released
                    ? AudioRecord.RECORDSTATE_RECORDING : AudioRecord.RECORDSTATE_STOPPED;
        }

        @Override
        public void startRecording() {
            started = true;
        }

        @Override
        public int read(short[] buffer, int offset, int size) {
            if (!sampleReturned) {
                readStarted.countDown();
                try {
                    sampleReady.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return AudioRecord.ERROR_DEAD_OBJECT;
                }
                if (released) {
                    return AudioRecord.ERROR_DEAD_OBJECT;
                }
                sampleReturned = true;
                int count = Math.min(size, 12);
                Arrays.fill(buffer, offset, offset + count, sample);
                return count;
            }
            return AudioRecord.ERROR_DEAD_OBJECT;
        }

        @Override
        public void stop() {
            started = false;
        }

        @Override
        public void release() {
            released = true;
            started = false;
            sampleReady.countDown();
        }
    }
}
