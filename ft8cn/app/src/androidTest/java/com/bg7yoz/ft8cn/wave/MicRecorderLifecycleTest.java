package com.bg7yoz.ft8cn.wave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioRecord;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises the real MicRecorder session state machine with deterministic fakes. */
@RunWith(AndroidJUnit4.class)
public class MicRecorderLifecycleTest {
    @Test
    public void permissionFailureDoesNotLeaveAServiceOrCreateAudioRecord() throws Exception {
        FakeService service = new FakeService(false);
        FakeFactory factory = new FakeFactory();
        CountDownLatch stopped = new CountDownLatch(1);
        MicRecorder recorder = newRecorder(factory, service, stopped);

        try {
            assertTrue(recorder.start());
            assertTrue("permission failure did not reach terminal state",
                    stopped.await(2, TimeUnit.SECONDS));
            assertFalse(recorder.isRunning());
            assertEquals(1, service.startCalls.get());
            assertEquals(0, service.stopCalls.get());
            assertEquals(0, factory.createCalls.get());
        } finally {
            recorder.stopRecord();
        }
    }

    @Test
    public void initializationFailureStopsTheStartedForegroundService() throws Exception {
        FakeService service = new FakeService(true);
        FakeFactory factory = new FakeFactory();
        factory.results.add(null);
        CountDownLatch stopped = new CountDownLatch(1);
        MicRecorder recorder = newRecorder(factory, service, stopped);

        try {
            assertTrue(recorder.start());
            assertTrue("initialization failure did not reach terminal state",
                    stopped.await(2, TimeUnit.SECONDS));
            assertFalse(recorder.isRunning());
            assertEquals(1, service.startCalls.get());
            assertEquals(1, service.stopCalls.get());
            assertEquals(service.startedIds, service.stoppedIds);
        } finally {
            recorder.stopRecord();
        }
    }

    @Test
    public void startFailureReleasesBothAttemptsAndStopsTheService() throws Exception {
        FakeService service = new FakeService(true);
        FakeFactory factory = new FakeFactory();
        FakeAudioRecord first = FakeAudioRecord.notRecording();
        FakeAudioRecord second = FakeAudioRecord.notRecording();
        factory.results.addAll(Arrays.asList(first, second));
        CountDownLatch stopped = new CountDownLatch(1);
        MicRecorder recorder = newRecorder(factory, service, stopped);

        try {
            assertTrue(recorder.start());
            assertTrue("start failure did not reach terminal state",
                    stopped.await(2, TimeUnit.SECONDS));
            assertFalse(recorder.isRunning());
            assertEquals(2, factory.createCalls.get());
            assertEquals(1, first.releaseCalls.get());
            assertEquals(1, second.releaseCalls.get());
            assertEquals(1, service.stopCalls.get());
        } finally {
            recorder.stopRecord();
        }
    }

    @Test
    public void stoppingAStartedSessionReleasesAudioAndTheMatchingService() throws Exception {
        FakeService service = new FakeService(true);
        FakeFactory factory = new FakeFactory();
        BlockingAudioRecord record = new BlockingAudioRecord();
        factory.results.add(record);
        CountDownLatch started = new CountDownLatch(1);
        MicRecorder recorder = new MicRecorder(factory, service);
        recorder.setOnStateListener((sessionId, running) -> {
            if (running) {
                started.countDown();
            }
        });

        try {
            assertTrue(recorder.start());
            assertTrue("successful recording did not become observable",
                    started.await(2, TimeUnit.SECONDS));
            assertTrue(recorder.isRunning());

            recorder.stopRecord();

            assertFalse(recorder.isRunning());
            assertTrue("AudioRecord release did not unblock the reader",
                    record.released.await(2, TimeUnit.SECONDS));
            assertEquals(1, record.releaseCalls.get());
            assertEquals(1, service.stopCalls.get());
            assertEquals(service.startedIds, service.stoppedIds);
        } finally {
            recorder.stopRecord();
        }
    }

    @Test
    public void stopThenStartCannotRetagAnInFlightOldDeliveryAsTheNewSession() throws Exception {
        FakeService service = new FakeService(true);
        FakeFactory factory = new FakeFactory();
        ControlledAudioRecord oldRecord = new ControlledAudioRecord((short) 11);
        ControlledAudioRecord newRecord = new ControlledAudioRecord((short) 22);
        factory.results.addAll(Arrays.asList(oldRecord, newRecord));
        List<Long> deliveredSessionIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch firstCallbackEntered = new CountDownLatch(1);
        CountDownLatch secondCallbackEntered = new CountDownLatch(1);
        CountDownLatch firstCallbackReturned = new CountDownLatch(1);
        CountDownLatch releaseFirstCallback = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        AtomicInteger callbackCount = new AtomicInteger();
        MicRecorder recorder = new MicRecorder(factory, service);
        recorder.setOnDataListener((sessionId, data, len) -> {
            deliveredSessionIds.add(sessionId);
            if (callbackCount.getAndIncrement() == 0) {
                firstCallbackEntered.countDown();
                try {
                    if (!releaseFirstCallback.await(2, TimeUnit.SECONDS)) {
                        throw new AssertionError("old delivery was not released");
                    }
                } catch (Throwable error) {
                    callbackFailure.set(error);
                } finally {
                    firstCallbackReturned.countDown();
                }
            } else {
                secondCallbackEntered.countDown();
            }
        });

        try {
            long firstSession = recorder.startSession();
            assertTrue(firstSession > 0);
            assertTrue("old reader did not produce a delivery",
                    oldRecord.readStarted.await(2, TimeUnit.SECONDS));
            oldRecord.sampleReady.countDown();
            assertTrue("old callback did not reach its interleave point",
                    firstCallbackEntered.await(2, TimeUnit.SECONDS));

            CountDownLatch stopReturned = new CountDownLatch(1);
            AtomicReference<Throwable> stopFailure = new AtomicReference<>();
            Thread stopper = new Thread(() -> {
                try {
                    recorder.stopSession(firstSession);
                } catch (Throwable error) {
                    stopFailure.set(error);
                } finally {
                    stopReturned.countDown();
                }
            }, "MicRecorderLifecycleTest-stop");
            stopper.start();
            assertTrue("stop blocked behind the old delivery callback",
                    stopReturned.await(2, TimeUnit.SECONDS));
            assertTrue("stopSession failed", stopFailure.get() == null);

            long secondSession = recorder.startSession();
            assertTrue(secondSession > firstSession);
            newRecord.sampleReady.countDown();
            assertTrue("new session did not produce a delivery",
                    secondCallbackEntered.await(2, TimeUnit.SECONDS));

            releaseFirstCallback.countDown();
            assertTrue("old callback did not return",
                    firstCallbackReturned.await(2, TimeUnit.SECONDS));
            stopper.join(2000);
            assertEquals(Arrays.asList(firstSession, secondSession), deliveredSessionIds);
            assertTrue("callback failure escaped", callbackFailure.get() == null);
        } finally {
            releaseFirstCallback.countDown();
            oldRecord.sampleReady.countDown();
            newRecord.sampleReady.countDown();
            recorder.stopRecord();
        }
    }

    private static MicRecorder newRecorder(FakeFactory factory, FakeService service,
                                            CountDownLatch stopped) {
        MicRecorder recorder = new MicRecorder(factory, service);
        recorder.setOnStateListener((sessionId, running) -> {
            if (!running) {
                stopped.countDown();
            }
        });
        return recorder;
    }

    private static final class FakeService implements ForegroundServiceController {
        final boolean startResult;
        final AtomicInteger startCalls = new AtomicInteger();
        final AtomicInteger stopCalls = new AtomicInteger();
        final List<Long> startedIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<Long> stoppedIds = new java.util.concurrent.CopyOnWriteArrayList<>();

        FakeService(boolean startResult) {
            this.startResult = startResult;
        }

        @Override
        public boolean start(long sessionId) {
            startCalls.incrementAndGet();
            startedIds.add(sessionId);
            return startResult;
        }

        @Override
        public void stop(long sessionId) {
            stopCalls.incrementAndGet();
            stoppedIds.add(sessionId);
        }
    }

    private static final class FakeFactory implements AudioRecordFactory {
        final List<AudioRecordHandle> results = new ArrayList<>();
        final AtomicInteger createCalls = new AtomicInteger();

        @Override
        public int getMinBufferSize() {
            return 1024;
        }

        @Override
        public AudioRecordHandle create(int bufferSize) {
            createCalls.incrementAndGet();
            return results.isEmpty() ? null : results.remove(0);
        }
    }

    private static class FakeAudioRecord implements AudioRecordHandle {
        private final boolean recording;
        final AtomicInteger releaseCalls = new AtomicInteger();
        boolean started;

        static FakeAudioRecord notRecording() {
            return new FakeAudioRecord(false);
        }

        FakeAudioRecord(boolean recording) {
            this.recording = recording;
        }

        @Override
        public int getState() {
            return AudioRecord.STATE_INITIALIZED;
        }

        @Override
        public int getRecordingState() {
            return started && recording
                    ? AudioRecord.RECORDSTATE_RECORDING : AudioRecord.RECORDSTATE_STOPPED;
        }

        @Override
        public void startRecording() {
            started = true;
        }

        @Override
        public int read(short[] buffer, int offset, int size) {
            return AudioRecord.ERROR_DEAD_OBJECT;
        }

        @Override
        public void stop() {
            started = false;
        }

        @Override
        public void release() {
            releaseCalls.incrementAndGet();
            started = false;
        }
    }

    private static final class BlockingAudioRecord extends FakeAudioRecord {
        final CountDownLatch released = new CountDownLatch(1);

        BlockingAudioRecord() {
            super(true);
        }

        @Override
        public int read(short[] buffer, int offset, int size) {
            try {
                released.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return AudioRecord.ERROR_DEAD_OBJECT;
        }

        @Override
        public void release() {
            super.release();
            released.countDown();
        }
    }

    private static final class ControlledAudioRecord extends FakeAudioRecord {
        final CountDownLatch readStarted = new CountDownLatch(1);
        final CountDownLatch sampleReady = new CountDownLatch(1);
        private final short sample;
        private boolean sampleReturned;

        ControlledAudioRecord(short sample) {
            super(true);
            this.sample = sample;
        }

        @Override
        public int read(short[] buffer, int offset, int size) {
            if (!sampleReturned) {
                readStarted.countDown();
                try {
                    if (!sampleReady.await(2, TimeUnit.SECONDS)) {
                        return AudioRecord.ERROR_DEAD_OBJECT;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return AudioRecord.ERROR_DEAD_OBJECT;
                }
                sampleReturned = true;
                buffer[offset] = sample;
                return 1;
            }
            return AudioRecord.ERROR_DEAD_OBJECT;
        }
    }
}
