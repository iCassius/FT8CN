package com.bg7yoz.ft8cn.ft8transmit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.bg7yoz.ft8cn.Ft8Message;
import com.bg7yoz.ft8cn.GeneralVariables;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class FT8TransmitSignalLifecycleTest {
    @After
    public void resetRadioMode() throws Throwable {
        onMain(() -> {
            GeneralVariables.connectMode = com.bg7yoz.ft8cn.connector.ConnectMode.USB_CABLE;
            GeneralVariables.controlMode = com.bg7yoz.ft8cn.database.ControlMode.RTS;
            GeneralVariables.pttDelay = 100;
        });
    }

    @Test
    public void stopDoesNotShutdownSharedExecutorAndRecreatedComponentCanSubmit() throws Throwable {
        RecordingExecutor executor = new RecordingExecutor();
        final FT8TransmitSignal[] first = new FT8TransmitSignal[1];
        final FT8TransmitSignal[] second = new FT8TransmitSignal[1];
        try {
            onMain(() -> {
                first[0] = newSignal(executor);
                first[0].setActivated(true);
                first[0].doTransmit();
                assertEquals(1, executor.submittedCount());

                first[0].stop();
                assertFalse(executor.isShutdown());

                second[0] = newSignal(executor);
                second[0].setActivated(true);
                second[0].doTransmit();
                assertEquals(2, executor.submittedCount());
            });
        } finally {
            onMain(() -> {
                if (first[0] != null) {
                    first[0].stop();
                }
                if (second[0] != null) {
                    second[0].stop();
                }
            });
            executor.shutdownNow();
        }
    }

    @Test
    public void stopRemovesForeverObserverAndIsIdempotent() throws Throwable {
        RecordingExecutor executor = new RecordingExecutor();
        final FT8TransmitSignal[] signal = new FT8TransmitSignal[1];
        try {
            onMain(() -> {
                signal[0] = newSignal(executor);
                assertTrue(GeneralVariables.mutableVolumePercent.hasObservers());

                signal[0].stop();
                assertFalse(GeneralVariables.mutableVolumePercent.hasObservers());

                signal[0].stop();
                signal[0].close();
            });
        } finally {
            onMain(() -> {
                if (signal[0] != null) {
                    signal[0].close();
                }
            });
            executor.shutdownNow();
        }
    }

    @Test
    public void stopTerminatesActiveNetworkTaskExactlyOnceAndFencesLateCallback() throws Throwable {
        RecordingExecutor executor = new RecordingExecutor();
        RecordingCallbacks callbacks = new RecordingCallbacks();
        final FT8TransmitSignal[] signal = new FT8TransmitSignal[1];
        Thread worker = null;
        try {
            onMain(() -> {
                GeneralVariables.connectMode = com.bg7yoz.ft8cn.connector.ConnectMode.NETWORK;
                GeneralVariables.pttDelay = 0;
                signal[0] = newSignal(executor, callbacks);
                signal[0].setActivated(true);
                signal[0].doTransmit();
            });
            worker = executor.startNext();
            assertTrue(callbacks.networkStarted.await(2, TimeUnit.SECONDS));

            onMain(() -> signal[0].stop());
            worker.join(2000);

            assertEquals(1, callbacks.beforeCount.get());
            assertEquals(1, callbacks.afterCount.get());
            assertFalse(signal[0].isTransmitting());
            onMain(() -> assertEquals(Boolean.FALSE, signal[0].mutableIsTransmitting.getValue()));
            assertFalse("late network completion must not call PTT OFF again", worker.isAlive());
            assertEquals(1, callbacks.afterCount.get());
        } finally {
            if (worker != null) {
                worker.join(2000);
            }
            onMain(() -> {
                if (signal[0] != null) {
                    signal[0].close();
                }
            });
            executor.shutdownNow();
        }
    }

    @Test
    public void stopTerminatesActiveCatTaskExactlyOnce() throws Throwable {
        RecordingExecutor executor = new RecordingExecutor();
        RecordingCallbacks callbacks = new RecordingCallbacks();
        final FT8TransmitSignal[] signal = new FT8TransmitSignal[1];
        Thread worker = null;
        try {
            onMain(() -> {
                GeneralVariables.connectMode = com.bg7yoz.ft8cn.connector.ConnectMode.USB_CABLE;
                GeneralVariables.controlMode = com.bg7yoz.ft8cn.database.ControlMode.CAT;
                GeneralVariables.pttDelay = 0;
                signal[0] = newSignal(executor, callbacks);
                signal[0].setActivated(true);
                signal[0].doTransmit();
            });
            worker = executor.startNext();
            assertTrue(callbacks.catStarted.await(2, TimeUnit.SECONDS));

            onMain(() -> signal[0].stop());
            worker.join(2000);

            assertEquals(1, callbacks.beforeCount.get());
            assertEquals(1, callbacks.afterCount.get());
            assertFalse(signal[0].isTransmitting());
            assertFalse(worker.isAlive());
        } finally {
            if (worker != null) {
                worker.join(2000);
            }
            onMain(() -> {
                if (signal[0] != null) {
                    signal[0].close();
                }
            });
            executor.shutdownNow();
        }
    }

    @Test
    public void oneHundredCreateStopCyclesLeaveNoObserversOrShutdownExecutor() throws Throwable {
        RecordingExecutor executor = new RecordingExecutor();
        try {
            onMain(() -> {
                for (int i = 0; i < 100; i++) {
                    FT8TransmitSignal signal = newSignal(executor);
                    signal.stop();
                    signal.close();
                }
                assertFalse(GeneralVariables.mutableVolumePercent.hasObservers());
                assertFalse(executor.isShutdown());
            });
        } finally {
            executor.shutdownNow();
        }
    }

    private static void onMain(Runnable action) throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private static FT8TransmitSignal newSignal(ExecutorService executor) {
        return new FT8TransmitSignal(null, null, null, executor);
    }

    private static FT8TransmitSignal newSignal(ExecutorService executor, OnDoTransmitted callbacks) {
        return new FT8TransmitSignal(null, callbacks, null, executor);
    }

    private static class RecordingCallbacks implements OnDoTransmitted {
        private final AtomicInteger beforeCount = new AtomicInteger();
        private final AtomicInteger afterCount = new AtomicInteger();
        private final CountDownLatch networkStarted = new CountDownLatch(1);
        private final CountDownLatch catStarted = new CountDownLatch(1);

        @Override
        public void onBeforeTransmit(Ft8Message message, int functionOder) {
            beforeCount.incrementAndGet();
        }

        @Override
        public void onAfterTransmit(Ft8Message message, int functionOder) {
            afterCount.incrementAndGet();
        }

        @Override
        public void onTransmitByWifi(Ft8Message message) {
            networkStarted.countDown();
        }

        @Override
        public boolean supportTransmitOverCAT() {
            return true;
        }

        @Override
        public void onTransmitOverCAT(Ft8Message message) {
            catStarted.countDown();
        }
    }

    private static class RecordingExecutor extends AbstractExecutorService {
        private final List<Runnable> submitted = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean shutdown;
        private int nextRunnable;

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new IllegalStateException("executor is shutdown");
            }
            submitted.add(command);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return new ArrayList<>(submitted);
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        int submittedCount() {
            return submitted.size();
        }

        Thread startNext() {
            Runnable command;
            synchronized (submitted) {
                command = submitted.get(nextRunnable++);
            }
            Thread worker = new Thread(command);
            worker.start();
            return worker;
        }
    }
}
