package com.bg7yoz.ft8cn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bg7yoz.ft8cn.flex.RadioTcpClient;
import com.bg7yoz.ft8cn.flex.RadioUdpClient;
import com.bg7yoz.ft8cn.icom.IcomUdpClient;
import com.bg7yoz.ft8cn.rigs.BaseRig;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class RadioNetworkClientTest {
    private static final int PACKET_COUNT = 128;

    @Test
    public void radioUdpCopiesPayloadAndPreservesOrder() throws Exception {
        DatagramSocket receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        receiver.setSoTimeout(3000);
        RadioUdpClient client = new RadioUdpClient(0);
        client.setActivated(true);
        try {
            for (int i = 0; i < PACKET_COUNT; i++) {
                byte[] payload = packet(i);
                client.sendData(payload, "127.0.0.1", receiver.getLocalPort());
                Arrays.fill(payload, (byte) 0x7f);
            }
            for (int i = 0; i < PACKET_COUNT; i++) {
                DatagramPacket received = new DatagramPacket(new byte[8], 8);
                receiver.receive(received);
                assertArrayEquals(packet(i), Arrays.copyOf(received.getData(), received.getLength()));
            }
        } finally {
            client.setActivated(false);
            receiver.close();
        }
    }

    @Test
    public void icomUdpCopiesPayloadAndPreservesOrder() throws Exception {
        DatagramSocket receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        receiver.setSoTimeout(3000);
        IcomUdpClient client = new IcomUdpClient();
        client.setActivated(true);
        try {
            for (int i = 0; i < PACKET_COUNT; i++) {
                byte[] payload = packet(i);
                client.sendData(payload, "127.0.0.1", receiver.getLocalPort());
                Arrays.fill(payload, (byte) 0x7f);
            }
            for (int i = 0; i < PACKET_COUNT; i++) {
                DatagramPacket received = new DatagramPacket(new byte[8], 8);
                receiver.receive(received);
                assertArrayEquals(packet(i), Arrays.copyOf(received.getData(), received.getLength()));
            }
        } finally {
            client.setActivated(false);
            receiver.close();
        }
    }

    @Test
    public void radioTcpSendsSnapshotsInOrderAndNotifiesOnceOnEof() throws Exception {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        server.setSoTimeout(3000);
        RadioTcpClient client = new RadioTcpClient();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicInteger closeCallbacks = new AtomicInteger();
        AtomicReference<byte[]> received = new AtomicReference<>();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        byte[] expected = expectedTcpPayload();

        client.setOnDataReceiveListener(new RadioTcpClient.OnDataReceiveListener() {
            @Override
            public void onConnectSuccess() {
                connected.countDown();
            }

            @Override
            public void onConnectFail() {
                serverFailure.compareAndSet(null, new AssertionError("TCP connect failed"));
            }

            @Override
            public void onDataReceive(byte[] buffer) {
            }

            @Override
            public void onConnectionClosed() {
                closeCallbacks.incrementAndGet();
                closed.countDown();
            }
        });

        Thread serverThread = new Thread(() -> {
            try (Socket socket = server.accept(); InputStream input = socket.getInputStream()) {
                byte[] buffer = new byte[expected.length];
                int offset = 0;
                while (offset < buffer.length) {
                    int read = input.read(buffer, offset, buffer.length - offset);
                    if (read < 0) {
                        throw new IOException("unexpected EOF before payload completed");
                    }
                    offset += read;
                }
                received.set(buffer);
            } catch (Throwable t) {
                serverFailure.compareAndSet(null, t);
            }
        });
        serverThread.start();

        try {
            client.connect("127.0.0.1", server.getLocalPort());
            assertTrue(connected.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < PACKET_COUNT; i++) {
                byte[] payload = packet(i);
                client.sendByte(payload);
                Arrays.fill(payload, (byte) 0x7f);
            }
            assertTrue(closed.await(2, TimeUnit.SECONDS));
            serverThread.join(2000);
            assertEquals(null, serverFailure.get());
            assertArrayEquals(expected, received.get());
            Thread.sleep(200);
            assertEquals(1, closeCallbacks.get());
        } finally {
            client.disconnect();
            server.close();
        }
    }

    @Test
    public void mainViewModelSendTaskSnapshotsTargetAndMessage() throws Exception {
        RecordingRig rig = new RecordingRig();
        Ft8Message first = new Ft8Message(1, 0, "TARGET-A", "SOURCE-A", "R-10");
        Ft8Message second = new Ft8Message(1, 0, "TARGET-B", "SOURCE-B", "R-11");
        MainViewModel.SendWaveDataRunnable firstTask = new MainViewModel.SendWaveDataRunnable(rig, first);
        MainViewModel.SendWaveDataRunnable secondTask = new MainViewModel.SendWaveDataRunnable(rig, second);
        first.callsignTo = "MUTATED-A";
        second.callsignTo = "MUTATED-B";

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(firstTask);
            executor.execute(secondTask);
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            assertEquals(Arrays.asList("TARGET-A", "TARGET-B"), rig.targets);
            assertEquals(Arrays.asList("SOURCE-A", "SOURCE-B"), rig.sources);
        } finally {
            executor.shutdownNow();
        }
    }

    private static byte[] packet(int sequence) {
        return new byte[]{(byte) sequence, (byte) (sequence >>> 8), 0x55, (byte) 0xaa};
    }

    private static byte[] expectedTcpPayload() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < PACKET_COUNT; i++) {
            output.write(packet(i), 0, packet(i).length);
        }
        return output.toByteArray();
    }

    private static final class RecordingRig extends BaseRig {
        private final List<String> targets = new ArrayList<>();
        private final List<String> sources = new ArrayList<>();

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void setUsbModeToRig() {
        }

        @Override
        public void setFreqToRig() {
        }

        @Override
        public void onReceiveData(byte[] data) {
        }

        @Override
        public void readFreqFromRig() {
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public synchronized void sendWaveData(Ft8Message message) {
            targets.add(message.getCallsignTo());
            sources.add(message.getCallsignFrom());
        }
    }
}
