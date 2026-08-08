package com.bg7yoz.ft8cn.ft8listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class DecodeCoordinatorTest {
    @Test
    public void emptyResultStillCompletesExactlyOnce() throws Exception {
        DecodeCoordinator coordinator = new DecodeCoordinator("decode-test-empty");
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger terminalCount = new AtomicInteger();
        AtomicReference<Boolean> cancelledResult = new AtomicReference<>();
        AtomicReference<Throwable> failureResult = new AtomicReference<>();
        List<List<String>> results = new ArrayList<>();
        try {
            assertTrue(coordinator.submit(token -> results.add(new ArrayList<>()),
                    new DecodeCoordinator.Listener() {
                        @Override
                        public void onStarted(long epoch) {
                        }

                        @Override
                        public void onFinished(long epoch, boolean cancelled, Throwable failure) {
                            terminalCount.incrementAndGet();
                            cancelledResult.set(cancelled);
                            failureResult.set(failure);
                            finished.countDown();
                        }
                    }));
            assertTrue("empty decode did not finish", finished.await(2, TimeUnit.SECONDS));
            awaitInactive(coordinator);
            assertEquals(1, terminalCount.get());
            assertEquals(Boolean.FALSE, cancelledResult.get());
            assertNull(failureResult.get());
            assertEquals(1, results.size());
            assertTrue(results.get(0).isEmpty());
        } finally {
            coordinator.stop();
        }
    }

    @Test
    public void exceptionStillProducesTerminalState() throws Exception {
        DecodeCoordinator coordinator = new DecodeCoordinator("decode-test-error");
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Boolean> cancelledResult = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger terminalCount = new AtomicInteger();
        try {
            assertTrue(coordinator.submit(token -> {
                throw new IllegalStateException("decode failed");
            }, new DecodeCoordinator.Listener() {
                @Override
                public void onStarted(long epoch) {
                }

                @Override
                public void onFinished(long epoch, boolean cancelled, Throwable error) {
                    terminalCount.incrementAndGet();
                    cancelledResult.set(cancelled);
                    failure.set(error);
                    finished.countDown();
                }
            }));
            assertTrue("failed decode did not finish", finished.await(2, TimeUnit.SECONDS));
            awaitInactive(coordinator);
            assertEquals(1, terminalCount.get());
            assertEquals(Boolean.FALSE, cancelledResult.get());
            assertNotNull(failure.get());
            assertTrue(failure.get() instanceof IllegalStateException);
        } finally {
            coordinator.stop();
        }
    }

    @Test
    public void overlappingSlotIsRejectedWhileOneDecodeIsActive() throws Exception {
        DecodeCoordinator coordinator = new DecodeCoordinator("decode-test-overlap");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        try {
            assertTrue(coordinator.submit(token -> {
                int nowActive = active.incrementAndGet();
                maxActive.updateAndGet(previous -> Math.max(previous, nowActive));
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                }
            }, noOpListener(firstFinished)));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            assertFalse("overlapping decode was admitted", coordinator.submit(
                    token -> { }, noOpListener(new CountDownLatch(1))));
            releaseFirst.countDown();
            assertTrue(firstFinished.await(2, TimeUnit.SECONDS));
            awaitInactive(coordinator);
            assertEquals(1, maxActive.get());
        } finally {
            releaseFirst.countDown();
            coordinator.stop();
        }
    }

    @Test
    public void cancelledOldEpochCannotWriteAfterTheNextEpochStarts() throws Exception {
        DecodeCoordinator coordinator = new DecodeCoordinator("decode-test-epoch");
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        CountDownLatch oldFinished = new CountDownLatch(1);
        CountDownLatch newFinished = new CountDownLatch(1);
        List<String> writes = new ArrayList<>();
        AtomicReference<Boolean> oldCancelled = new AtomicReference<>();
        try {
            assertTrue(coordinator.submit(token -> {
                oldStarted.countDown();
                try {
                    releaseOld.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                token.throwIfCancelled();
                writes.add("old");
            }, new DecodeCoordinator.Listener() {
                @Override
                public void onStarted(long epoch) {
                }

                @Override
                public void onFinished(long epoch, boolean cancelled, Throwable failure) {
                    oldCancelled.set(cancelled);
                    oldFinished.countDown();
                }
            }));
            assertTrue(oldStarted.await(2, TimeUnit.SECONDS));
            coordinator.cancelActive();
            assertTrue(oldFinished.await(2, TimeUnit.SECONDS));
            awaitInactive(coordinator);
            assertTrue(Boolean.TRUE.equals(oldCancelled.get()));
            assertFalse("cancelled old result was written", writes.contains("old"));

            assertTrue(coordinator.submit(token -> writes.add("new"),
                    new DecodeCoordinator.Listener() {
                        @Override
                        public void onStarted(long epoch) {
                        }

                        @Override
                        public void onFinished(long epoch, boolean cancelled, Throwable failure) {
                            assertFalse(cancelled);
                            assertEquals(null, failure);
                            newFinished.countDown();
                        }
                    }));
            assertTrue("new epoch did not finish", newFinished.await(2, TimeUnit.SECONDS));
            awaitInactive(coordinator);
            assertEquals(1, writes.size());
            assertEquals("new", writes.get(0));
        } finally {
            releaseOld.countDown();
            coordinator.stop();
        }
    }

    @Test
    public void cancelBeforeTaskStartsClearsTheSlotAndDoesNotRunTheOldTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        executor.submit(() -> {
            blockerStarted.countDown();
            try {
                releaseBlocker.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        DecodeCoordinator coordinator = new DecodeCoordinator("decode-test-queued-cancel", executor);
        CountDownLatch newFinished = new CountDownLatch(1);
        AtomicInteger oldRuns = new AtomicInteger();
        try {
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));
            assertTrue(coordinator.submit(token -> oldRuns.incrementAndGet(), noOpListener(
                    new CountDownLatch(1))));

            coordinator.cancelActive();
            assertFalse("queued cancellation left the coordinator active", coordinator.isActive());
            assertTrue("new decode was blocked by a cancelled queued Future", coordinator.submit(
                    token -> { }, noOpListener(newFinished)));

            releaseBlocker.countDown();
            assertTrue("new decode did not finish", newFinished.await(2, TimeUnit.SECONDS));
            assertEquals("cancelled queued decode still ran", 0, oldRuns.get());
            awaitInactive(coordinator);
        } finally {
            releaseBlocker.countDown();
            coordinator.stop();
            executor.shutdownNow();
        }
    }

    @Test
    public void runningOldFinallyCannotClearAStillActiveNewRun() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger terminalCleanupCount = new AtomicInteger();
        CountDownLatch oldCleanup = new CountDownLatch(1);
        DecodeCoordinator coordinator = new DecodeCoordinator("decode-test-finally-identity", executor,
                () -> {
                    if (terminalCleanupCount.incrementAndGet() == 1) {
                        oldCleanup.countDown();
                    }
                });
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch oldFinishedCallback = new CountDownLatch(1);
        CountDownLatch releaseOldFinishedCallback = new CountDownLatch(1);
        CountDownLatch newStarted = new CountDownLatch(1);
        CountDownLatch releaseNew = new CountDownLatch(1);
        CountDownLatch newFinished = new CountDownLatch(1);
        try {
            assertTrue(coordinator.submit(token -> {
                oldStarted.countDown();
                token.throwIfCancelled();
            }, new DecodeCoordinator.Listener() {
                @Override
                public void onStarted(long epoch) {
                }

                @Override
                public void onFinished(long epoch, boolean cancelled, Throwable failure) {
                    oldFinishedCallback.countDown();
                    try {
                        if (!releaseOldFinishedCallback.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("old terminal callback was not released");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("old terminal callback was interrupted", interrupted);
                    }
                }
            }));
            assertTrue(oldStarted.await(2, TimeUnit.SECONDS));
            assertTrue(oldFinishedCallback.await(2, TimeUnit.SECONDS));
            assertTrue("new decode was not admitted after old body terminated", coordinator.submit(
                    token -> {
                        newStarted.countDown();
                        if (!releaseNew.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("new decode was not released");
                        }
                    }, noOpListener(newFinished)));
            assertTrue("new decode did not start while old terminal callback was held",
                    newStarted.await(2, TimeUnit.SECONDS));

            releaseOldFinishedCallback.countDown();
            // The old callback now returns and its finally path runs while
            // the new task is still active. ActiveRun identity must preserve
            // the new slot.
            assertTrue("old terminal cleanup did not finish",
                    oldCleanup.await(2, TimeUnit.SECONDS));
            assertTrue(coordinator.isActive());
            assertFalse("old finally cleared the newly accepted run", coordinator.submit(
                    token -> { }, noOpListener(new CountDownLatch(1))));
            releaseNew.countDown();
            assertTrue("new decode did not finish", newFinished.await(2, TimeUnit.SECONDS));
            awaitInactive(coordinator);
        } finally {
            releaseOldFinishedCallback.countDown();
            releaseNew.countDown();
            coordinator.stop();
            executor.shutdownNow();
        }
    }

    @Test
    public void stopIsIdempotentAndRejectsWorkAfterExecutorShutdown() {
        DecodeCoordinator coordinator = new DecodeCoordinator("decode-test-stop-idempotent");
        coordinator.stop();
        coordinator.stop();

        assertFalse(coordinator.submit(token -> { }, noOpListener(new CountDownLatch(1))));
    }

    private static DecodeCoordinator.Listener noOpListener(CountDownLatch finished) {
        return new DecodeCoordinator.Listener() {
            @Override
            public void onStarted(long epoch) {
            }

            @Override
            public void onFinished(long epoch, boolean cancelled, Throwable failure) {
                finished.countDown();
            }
        };
    }

    private static void awaitInactive(DecodeCoordinator coordinator) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (coordinator.isActive() && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertFalse("decode coordinator remained active", coordinator.isActive());
    }
}
