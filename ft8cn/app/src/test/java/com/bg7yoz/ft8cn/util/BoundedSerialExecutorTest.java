package com.bg7yoz.ft8cn.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class BoundedSerialExecutorTest {
    @Test
    public void tasksRunInSubmissionOrder() throws Exception {
        BoundedSerialExecutor executor = new BoundedSerialExecutor(4);
        List<Integer> order = new ArrayList<>();
        Object orderLock = new Object();
        CountDownLatch done = new CountDownLatch(100);
        try {
            for (int i = 0; i < 100; i++) {
                final int sequence = i;
                executor.submit(() -> {
                    synchronized (orderLock) {
                        order.add(sequence);
                    }
                    done.countDown();
                }, 2, TimeUnit.SECONDS);
            }

            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(100, order.size());
            for (int i = 0; i < order.size(); i++) assertEquals(i, (int) order.get(i));
        } finally {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void fullQueueIsExplicitAndInterruptedSubmissionReturns() throws Exception {
        BoundedSerialExecutor executor = new BoundedSerialExecutor(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            executor.submit(() -> { });
            try {
                executor.submit(() -> { });
                throw new AssertionError("full queue was silently accepted");
            } catch (RejectedExecutionException expected) {
                // Explicit policy is observable by the caller.
            }

            AtomicReference<Throwable> result = new AtomicReference<>();
            Thread blockedSubmitter = new Thread(() -> {
                try {
                    executor.submit(() -> { }, 10, TimeUnit.SECONDS);
                    result.set(new AssertionError("timed submit unexpectedly completed"));
                } catch (InterruptedException expected) {
                    result.set(expected);
                } catch (Throwable t) {
                    result.set(t);
                }
            });
            blockedSubmitter.start();
            Thread.sleep(50);
            blockedSubmitter.interrupt();
            blockedSubmitter.join(1000);
            assertTrue(!blockedSubmitter.isAlive());
            assertTrue(result.get() instanceof InterruptedException);
        } finally {
            release.countDown();
            executor.shutdown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    public void pendingTasksCanBeCancelledAndClosedExecutorRejects() throws Exception {
        BoundedSerialExecutor executor = new BoundedSerialExecutor(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            executor.submit(() -> { });
            executor.submit(() -> { });
            executor.cancelPending();
            release.countDown();
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
        try {
            executor.submit(() -> { });
            throw new AssertionError("closed executor accepted work");
        } catch (RejectedExecutionException expected) {
            // expected
        }
    }

    @Test
    public void oneHundredExecutorLifecyclesCloseWithoutLeakingWork() throws Exception {
        for (int i = 0; i < 100; i++) {
            BoundedSerialExecutor executor = new BoundedSerialExecutor(2);
            CountDownLatch ran = new CountDownLatch(1);
            executor.submit(ran::countDown);
            executor.shutdown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
            assertTrue(executor.isShutdown());
        }
    }
}
