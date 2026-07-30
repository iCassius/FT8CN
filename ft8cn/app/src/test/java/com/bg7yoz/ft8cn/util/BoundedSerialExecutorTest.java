package com.bg7yoz.ft8cn.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class BoundedSerialExecutorTest {
    @Test
    public void tasksRunInSubmissionOrder() throws Exception {
        BoundedSerialExecutor executor = new BoundedSerialExecutor(4);
        List<Integer> order = new ArrayList<>();
        Object orderLock = new Object();
        CountDownLatch done = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            final int sequence = i;
            executor.execute(() -> {
                synchronized (orderLock) {
                    order.add(sequence);
                }
                done.countDown();
            });
        }

        org.junit.Assert.assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(100, order.size());
        for (int i = 0; i < order.size(); i++) {
            assertEquals(i, (int) order.get(i));
        }
    }
}
