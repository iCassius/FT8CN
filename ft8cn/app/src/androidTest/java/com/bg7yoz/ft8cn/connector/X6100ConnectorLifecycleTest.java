package com.bg7yoz.ft8cn.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bg7yoz.ft8cn.flex.RadioTcpClient;
import com.bg7yoz.ft8cn.rigs.OnRigStateChanged;
import com.bg7yoz.ft8cn.x6100.X6100Radio;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class X6100ConnectorLifecycleTest {
    @Test
    public void earlyEofBeforeBaseRigAttachmentIsNullSafeAndCancelsInitialization() throws Exception {
        FakeX6100Radio radio = new FakeX6100Radio(20);
        X6100Connector connector = new X6100Connector(
                ApplicationProvider.getApplicationContext(), radio, 0, 20, 100);
        CountDownLatch disconnected = new CountDownLatch(1);
        AtomicInteger disconnectCount = new AtomicInteger();
        connector.setOnRigStateChanged(new NoOpRigStateChanged() {
            @Override public void onDisconnected() {
                disconnectCount.incrementAndGet();
                disconnected.countDown();
            }
        });

        radio.getOnTcpConnectStatus().onConnectSuccess(null);
        assertTrue(radio.firstOpen.await(1, TimeUnit.SECONDS));
        int callsAtClose = radio.totalCommands();
        radio.connected = false;
        // baseRig is deliberately still null: this is the connect()/setBaseRig() window.
        radio.getOnTcpConnectStatus().onConnectionClosed(null);

        assertTrue(disconnected.await(1, TimeUnit.SECONDS));
        Thread.sleep(250);
        assertEquals(1, disconnectCount.get());
        assertFalse(connector.isConnected());
        assertFalse(connector.isStreamInitializationRunningForTest());
        assertEquals("closed sessions must not keep retrying", callsAtClose, radio.totalCommands());
    }

    @Test
    public void streamInitializationStopsAtConfiguredRetryLimit() throws Exception {
        FakeX6100Radio radio = new FakeX6100Radio(3);
        X6100Connector connector = new X6100Connector(
                ApplicationProvider.getApplicationContext(), radio, 0, 3, 5);

        radio.getOnTcpConnectStatus().onConnectSuccess(null);
        assertTrue(radio.allOpenAttempts.await(1, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (connector.isStreamInitializationRunningForTest()
                && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }

        assertFalse(connector.isStreamInitializationRunningForTest());
        assertEquals(3, radio.openCommands.get());
        assertEquals(3, radio.audioCommands.get());
        assertEquals(3, radio.meterCommands.get());
        radio.connected = false;
        radio.getOnTcpConnectStatus().onConnectionClosed(null);
    }

    private static class NoOpRigStateChanged implements OnRigStateChanged {
        @Override public void onDisconnected() { }
        @Override public void onConnected() { }
        @Override public void onPttChanged(boolean isOn) { }
        @Override public void onFreqChanged(long freq) { }
        @Override public void onRunError(String message) { }
    }

    private static final class FakeX6100Radio extends X6100Radio {
        final AtomicInteger openCommands = new AtomicInteger();
        final AtomicInteger audioCommands = new AtomicInteger();
        final AtomicInteger meterCommands = new AtomicInteger();
        final CountDownLatch firstOpen = new CountDownLatch(1);
        final CountDownLatch allOpenAttempts;
        volatile boolean connected = true;

        FakeX6100Radio(int expectedOpenAttempts) {
            allOpenAttempts = new CountDownLatch(expectedOpenAttempts);
        }

        @Override public boolean isConnect() {
            return connected;
        }

        @Override public synchronized void commandOpenStream() {
            openCommands.incrementAndGet();
            firstOpen.countDown();
            allOpenAttempts.countDown();
        }

        @Override public synchronized void commandGetAudioInfo() {
            audioCommands.incrementAndGet();
        }

        @Override public synchronized void commandSubAllMeter() {
            meterCommands.incrementAndGet();
        }

        int totalCommands() {
            return openCommands.get() + audioCommands.get() + meterCommands.get();
        }
    }
}
