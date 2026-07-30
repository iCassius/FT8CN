package com.bg7yoz.ft8cn.ft8transmit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.bg7yoz.ft8cn.GeneralVariables;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class FT8TransmitSignalLifecycleTest {
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

    private static class RecordingExecutor extends AbstractExecutorService {
        private final List<Runnable> submitted = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean shutdown;

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
    }
}
