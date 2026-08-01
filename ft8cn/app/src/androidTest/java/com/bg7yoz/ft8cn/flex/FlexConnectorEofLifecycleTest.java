package com.bg7yoz.ft8cn.flex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bg7yoz.ft8cn.connector.FlexConnector;
import com.bg7yoz.ft8cn.rigs.OnRigStateChanged;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class FlexConnectorEofLifecycleTest {
    @Test
    public void remoteEofReachesDisconnectOwnerAndTerminatesActiveTransmitState() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        ServerSocket server = new ServerSocket(0, 1, loopback);
        CountDownLatch accepted = new CountDownLatch(1);
        Thread peer = new Thread(() -> {
            try (Socket socket = server.accept()) {
                accepted.countDown();
                Thread.sleep(100);
            } catch (Exception ignored) {
            }
        }, "flex-eof-peer");
        peer.start();

        RadioTcpClient tcpClient = new RadioTcpClient();
        FlexRadio radio = new FlexRadio(tcpClient);
        FlexConnector connector = new FlexConnector(
                ApplicationProvider.getApplicationContext(), radio, 0);
        CountDownLatch disconnected = new CountDownLatch(1);
        AtomicInteger disconnectCount = new AtomicInteger();
        AtomicBoolean transmitting = new AtomicBoolean(true);
        AtomicBoolean pttOn = new AtomicBoolean(true);
        AtomicBoolean scoStopped = new AtomicBoolean(true);
        connector.setOnRigStateChanged(new OnRigStateChanged() {
            @Override public void onDisconnected() {
                // Mirrors MainViewModel's disconnect owner: stop transmission, PTT OFF,
                // and restore SCO as one terminal transition.
                disconnectCount.incrementAndGet();
                transmitting.set(false);
                pttOn.set(false);
                scoStopped.set(false);
                disconnected.countDown();
            }
            @Override public void onConnected() { }
            @Override public void onPttChanged(boolean isOn) { }
            @Override public void onFreqChanged(long freq) { }
            @Override public void onRunError(String message) { }
        });

        try {
            radio.connect("127.0.0.1", server.getLocalPort());
            assertTrue(accepted.await(2, TimeUnit.SECONDS));
            assertTrue("remote EOF must propagate through FlexConnector",
                    disconnected.await(3, TimeUnit.SECONDS));
            assertEquals(1, disconnectCount.get());
            assertFalse("active transmission must reach terminal state", transmitting.get());
            assertFalse("PTT must be off after disconnect", pttOn.get());
            assertFalse("SCO must be restored after disconnect", scoStopped.get());
            assertFalse(radio.isConnect());
        } finally {
            radio.disConnect();
            server.close();
            peer.join(1000);
        }
    }
}
