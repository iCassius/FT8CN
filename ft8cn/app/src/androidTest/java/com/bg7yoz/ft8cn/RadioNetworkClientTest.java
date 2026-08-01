package com.bg7yoz.ft8cn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bg7yoz.ft8cn.flex.RadioTcpClient;
import com.bg7yoz.ft8cn.flex.RadioUdpClient;
import com.bg7yoz.ft8cn.icom.IcomUdpClient;
import com.bg7yoz.ft8cn.icom.IComPacketTypes;
import com.bg7yoz.ft8cn.icom.IcomAudioUdp;
import com.bg7yoz.ft8cn.icom.XieGuAudioUdp;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class RadioNetworkClientTest {
    private static final int PACKET_COUNT = 128;
    private static final InetAddress IPV4_LOOPBACK = ipv4Loopback();

    @Test
    public void radioUdpCopiesPayloadAndPreservesOrder() throws Exception {
        DatagramSocket receiver = new DatagramSocket(0, IPV4_LOOPBACK);
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
        DatagramSocket receiver = new DatagramSocket(0, IPV4_LOOPBACK);
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
    public void udpOldReceiveSessionCannotCloseNewSocket() throws Exception {
        RadioUdpClient client = new RadioUdpClient(0);
        CountDownLatch received = new CountDownLatch(1);
        client.setOnUdpEvents((socket, packet, data) -> received.countDown());
        DatagramSocket sender = new DatagramSocket(0, IPV4_LOOPBACK);
        try {
            for (int i = 0; i < 100; i++) {
                client.setActivated(true);
                assertTrue(client.isActivated());
                client.setActivated(false);
            }
            client.setActivated(true);
            int port = client.getPort();
            byte[] payload = packet(99);
            sender.send(new DatagramPacket(payload, payload.length, IPV4_LOOPBACK, port));
            assertTrue(received.await(2, TimeUnit.SECONDS));
            assertTrue(client.isActivated());
        } finally {
            client.setActivated(false);
            sender.close();
        }
    }

    @Test
    public void icomUdpOldReceiveSessionCannotCloseNewSocket() throws Exception {
        IcomUdpClient client = new IcomUdpClient();
        CountDownLatch received = new CountDownLatch(1);
        client.setOnUdpEvents(new IcomUdpClient.OnUdpEvents() {
            @Override
            public void OnReceiveData(DatagramSocket socket, DatagramPacket packet, byte[] data) {
                received.countDown();
            }

            @Override
            public void OnUdpSendIOException(IOException e) {
            }
        });
        DatagramSocket sender = new DatagramSocket(0, IPV4_LOOPBACK);
        try {
            for (int i = 0; i < 100; i++) {
                client.setActivated(true);
                assertTrue(client.isActivated());
                client.setActivated(false);
            }
            client.setActivated(true);
            int port = client.getLocalPort();
            byte[] payload = packet(100);
            sender.send(new DatagramPacket(payload, payload.length, IPV4_LOOPBACK, port));
            assertTrue(received.await(2, TimeUnit.SECONDS));
            assertTrue(client.isActivated());
        } finally {
            client.setActivated(false);
            sender.close();
        }
    }

    @Test
    public void radioTcpSendsSnapshotsInOrderAndNotifiesOnceOnEof() throws Exception {
        ServerSocket server = new ServerSocket(0, 1, IPV4_LOOPBACK);
        server.setSoTimeout(3000);
        RadioTcpClient client = new RadioTcpClient();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicInteger closeCallbacks = new AtomicInteger();
        AtomicLong eofAtNanos = new AtomicLong();
        AtomicLong closeCallbackAtNanos = new AtomicLong();
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
                closeCallbackAtNanos.set(System.nanoTime());
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
                eofAtNanos.set(System.nanoTime());
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
            assertTrue(closeCallbackAtNanos.get() - eofAtNanos.get() <= 200_000_000L);
        } finally {
            client.disconnect();
            server.close();
        }
    }

    @Test
    public void radioTcpOldQueuedTasksCannotWriteNewConnection() throws Exception {
        ServerSocket firstServer = new ServerSocket(0, 1, IPV4_LOOPBACK);
        ServerSocket secondServer = new ServerSocket(0, 1, IPV4_LOOPBACK);
        CountDownLatch firstAccepted = new CountDownLatch(1);
        CountDownLatch secondReceived = new CountDownLatch(1);
        CountDownLatch firstConnected = new CountDownLatch(1);
        CountDownLatch secondConnected = new CountDownLatch(1);
        AtomicInteger connectionCount = new AtomicInteger();
        AtomicReference<byte[]> markerReceived = new AtomicReference<>();
        RadioTcpClient client = new RadioTcpClient();
        client.setOnDataReceiveListener(new RadioTcpClient.OnDataReceiveListener() {
            @Override
            public void onConnectSuccess() {
                if (connectionCount.incrementAndGet() == 1) firstConnected.countDown();
                else secondConnected.countDown();
            }

            @Override
            public void onConnectFail() {
            }

            @Override
            public void onDataReceive(byte[] buffer) {
            }

            @Override
            public void onConnectionClosed() {
            }
        });
        Thread firstThread = new Thread(() -> {
            try (Socket socket = firstServer.accept()) {
                firstAccepted.countDown();
                Thread.sleep(1000);
            } catch (Throwable ignored) {
            }
        });
        Thread secondThread = new Thread(() -> {
            try (Socket socket = secondServer.accept(); InputStream input = socket.getInputStream()) {
                byte[] marker = new byte[]{0x44, 0x55, 0x66, 0x77};
                byte[] received = new byte[marker.length];
                int offset = 0;
                while (offset < received.length) {
                    int read = input.read(received, offset, received.length - offset);
                    if (read < 0) return;
                    offset += read;
                }
                markerReceived.set(received);
                secondReceived.countDown();
            } catch (Throwable ignored) {
            }
        });
        firstThread.start();
        secondThread.start();
        try {
            client.connect("127.0.0.1", firstServer.getLocalPort());
            assertTrue(firstConnected.await(2, TimeUnit.SECONDS));
            assertTrue(firstAccepted.await(1, TimeUnit.SECONDS));
            byte[] stale = new byte[64 * 1024];
            Arrays.fill(stale, (byte) 0x22);
            for (int i = 0; i < 128; i++) client.sendByte(stale);

            client.connect("127.0.0.1", secondServer.getLocalPort());
            assertTrue(secondConnected.await(2, TimeUnit.SECONDS));
            byte[] marker = new byte[]{0x44, 0x55, 0x66, 0x77};
            client.sendByte(marker);
            assertTrue(secondReceived.await(2, TimeUnit.SECONDS));
            assertArrayEquals(marker, markerReceived.get());
        } finally {
            client.disconnect();
            firstServer.close();
            secondServer.close();
            firstThread.join(1500);
            secondThread.join(1500);
        }
    }

    @Test
    public void radioTcpOneHundredReconnectsLeaveNoOldSessionActive() throws Exception {
        final int reconnectCount = 100;
        ServerSocket server = new ServerSocket(0, 1, IPV4_LOOPBACK);
        Semaphore connected = new Semaphore(0);
        AtomicInteger connectCallbacks = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        RadioTcpClient client = new RadioTcpClient();
        client.setOnDataReceiveListener(new RadioTcpClient.OnDataReceiveListener() {
            @Override
            public void onConnectSuccess() {
                connectCallbacks.incrementAndGet();
                connected.release();
            }

            @Override
            public void onConnectFail() {
                failure.compareAndSet(null, new AssertionError("connect failed"));
                connected.release();
            }

            @Override
            public void onDataReceive(byte[] buffer) {
            }

            @Override
            public void onConnectionClosed() {
            }
        });
        Thread serverThread = new Thread(() -> {
            try {
                for (int i = 0; i < reconnectCount; i++) {
                    try (Socket socket = server.accept(); InputStream input = socket.getInputStream()) {
                        while (input.read() >= 0) {
                            // Wait until the client closes this exact session.
                        }
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });
        serverThread.start();
        try {
            for (int i = 0; i < reconnectCount; i++) {
                client.connect("127.0.0.1", server.getLocalPort());
                assertTrue("reconnect " + i, connected.tryAcquire(2, TimeUnit.SECONDS));
                assertEquals(null, failure.get());
                client.disconnect();
            }
            serverThread.join(3000);
            assertTrue(!serverThread.isAlive());
            assertEquals(reconnectCount, connectCallbacks.get());
            assertTrue(!client.isConnect());
        } finally {
            client.disconnect();
            server.close();
            serverThread.join(1000);
        }
    }

    @Test
    public void audioSendersKeepImmutableSnapshotsAndXieGuCanRestart() throws Exception {
        CapturingIcomAudio icom = new CapturingIcomAudio(16);
        CapturingXieGuAudio xiegu = new CapturingXieGuAudio(4);
        try {
            icom.isPttOn = true;
            float[] first = new float[240];
            first[0] = 0.25f;
            float[] second = new float[240];
            second[0] = -0.25f;
            icom.sendTxAudioData(first);
            Arrays.fill(first, 0.9f);
            icom.sendTxAudioData(second);
            assertTrue(icom.packetLatch.await(2, TimeUnit.SECONDS));
            assertTrue("ICOM packets=" + describePackets(icom.packets),
                    containsAudioSample(icom.packets, sampleBytes(0.25f)));
            assertTrue("ICOM packets=" + describePackets(icom.packets),
                    containsAudioSample(icom.packets, sampleBytes(-0.25f)));

            xiegu.isPttOn = true;
            xiegu.startTxAudio();
            float[] xieguFirst = new float[960];
            xieguFirst[0] = 0.25f;
            float[] xieguSecond = new float[960];
            xieguSecond[0] = -0.25f;
            xiegu.sendTxAudioData(xieguFirst);
            Arrays.fill(xieguFirst, 0.9f);
            xiegu.sendTxAudioData(xieguSecond);
            assertTrue(xiegu.packetLatch.await(2, TimeUnit.SECONDS));
            assertTrue("XieGu packets=" + describePackets(xiegu.packets),
                    containsAudioSample(xiegu.packets, sampleBytes(0.25f)));
            xiegu.stopTXAudio();
            xiegu.startTxAudio();
        } finally {
            icom.stopTXAudio();
            xiegu.stopTXAudio();
        }
    }

    private static byte[] sampleBytes(float sample) {
        return IComPacketTypes.shortToBigEndian((short) (sample * 32767.0f
                * com.bg7yoz.ft8cn.GeneralVariables.volumePercent));
    }

    private static boolean containsAudioSample(List<byte[]> packets, byte[] expected) {
        synchronized (packets) {
            for (byte[] packet : packets) {
                if (packet.length >= expected.length
                        && packet[0] == expected[0] && packet[1] == expected[1]) return true;
            }
        }
        return false;
    }

    private static String describePackets(List<byte[]> packets) {
        StringBuilder result = new StringBuilder();
        synchronized (packets) {
            for (int i = 0; i < Math.min(12, packets.size()); i++) {
                byte[] packet = packets.get(i);
                result.append('[');
                for (int j = 0; j < Math.min(4, packet.length); j++) {
                    result.append(String.format("%02x", packet[j] & 0xff));
                }
                result.append(']');
            }
        }
        return result.toString();
    }

    private static final class CapturingIcomAudio extends IcomAudioUdp {
        private final List<byte[]> packets = new ArrayList<>();
        private final CountDownLatch packetLatch;

        CapturingIcomAudio(int packetCount) {
            packetLatch = new CountDownLatch(packetCount);
        }

        @Override
        public synchronized com.bg7yoz.ft8cn.util.SubmissionResult sendTrackedPacket(byte[] data) {
            synchronized (packets) {
                packets.add(IComPacketTypes.AudioPacket.getAudioData(data));
            }
            packetLatch.countDown();
            return com.bg7yoz.ft8cn.util.SubmissionResult.ENQUEUED;
        }
    }

    private static final class CapturingXieGuAudio extends XieGuAudioUdp {
        private final List<byte[]> packets = new ArrayList<>();
        private final CountDownLatch packetLatch;

        CapturingXieGuAudio(int packetCount) {
            packetLatch = new CountDownLatch(packetCount);
        }

        @Override
        public synchronized com.bg7yoz.ft8cn.util.SubmissionResult sendTrackedPacket(byte[] data) {
            synchronized (packets) {
                packets.add(IComPacketTypes.AudioPacket.getAudioData(data));
            }
            packetLatch.countDown();
            return com.bg7yoz.ft8cn.util.SubmissionResult.ENQUEUED;
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

    private static InetAddress ipv4Loopback() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
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
