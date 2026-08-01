package com.bg7yoz.ft8cn.flex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ApplicationProvider;

import com.bg7yoz.ft8cn.connector.FlexConnector;
import com.bg7yoz.ft8cn.util.BoundedSerialExecutor;
import com.bg7yoz.ft8cn.util.SubmissionResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class RadioTcpClientCancellationTest {
    private static final InetAddress LOOPBACK = loopback();

    @Test
    public void disconnectClosesSocketWhileConnectIsPending() throws Exception {
        BlockingSocket socket = new BlockingSocket();
        RadioTcpClient client = new RadioTcpClient(() -> socket);
        AtomicInteger connectFailures = new AtomicInteger();
        client.setOnDataReceiveListener(new RadioTcpClient.OnDataReceiveListener() {
            @Override public void onConnectSuccess() { }
            @Override public void onConnectFail() { connectFailures.incrementAndGet(); }
            @Override public void onDataReceive(byte[] buffer) { }
            @Override public void onConnectionClosed() { }
        });

        client.connect("127.0.0.1", 12345);
        assertTrue(socket.connectEntered.await(1, TimeUnit.SECONDS));
        client.disconnect();

        assertTrue(socket.closeCalled.await(1, TimeUnit.SECONDS));
        assertTrue(socket.connectFinished.await(1, TimeUnit.SECONDS));
        assertFalse(client.isConnect());
        assertEquals("a locally cancelled session must not report a connection failure", 0,
                connectFailures.get());
    }

    @Test
    public void tcpAndRadioUdpReturnRejectedWhenTheirQueuesAreClosed() throws Exception {
        BoundedSerialExecutor tcpQueue = new BoundedSerialExecutor(1);
        ServerSocket server = new ServerSocket(0, 1, LOOPBACK);
        CountDownLatch connected = new CountDownLatch(1);
        RadioTcpClient client = new RadioTcpClient(Socket::new, tcpQueue);
        client.setOnDataReceiveListener(new RadioTcpClient.OnDataReceiveListener() {
            @Override public void onConnectSuccess() { connected.countDown(); }
            @Override public void onConnectFail() { }
            @Override public void onDataReceive(byte[] buffer) { }
            @Override public void onConnectionClosed() { }
        });
        Thread accept = new Thread(() -> {
            try (Socket ignored = server.accept()) {
                Thread.sleep(500);
            } catch (Exception ignored) {
            }
        });
        accept.start();
        try {
            client.connect("127.0.0.1", server.getLocalPort());
            assertTrue(connected.await(1, TimeUnit.SECONDS));
            tcpQueue.shutdown();
            assertEquals(SubmissionResult.REJECTED, client.sendByte(new byte[]{1}));
            FlexRadio flex = new FlexRadio(client);
            int flexSequence = flex.getCommandSequence();
            assertEquals(SubmissionResult.REJECTED,
                    flex.sendCommand(FlexCommand.PTT_ON, "xmit 1"));
            assertEquals(flexSequence, flex.getCommandSequence());
            FlexConnector flexConnector = new FlexConnector(
                    ApplicationProvider.getApplicationContext(), flex, 0);
            flexConnector.setPttOn(true);
            assertFalse("PTT state must not advance after a rejected command", flex.isPttOn);

            BoundedSerialExecutor udpQueue = new BoundedSerialExecutor(1);
            RadioUdpClient udp = new RadioUdpClient(0, udpQueue);
            udp.setActivated(true);
            try {
                udpQueue.shutdown();
                assertEquals(SubmissionResult.REJECTED,
                        udp.sendData(new byte[]{1}, "127.0.0.1", 9));
            } finally {
                udp.setActivated(false);
            }
        } finally {
            client.disconnect();
            server.close();
            accept.join(1000);
        }
    }

    private static InetAddress loopback() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }

    private static final class BlockingSocket extends Socket {
        final CountDownLatch connectEntered = new CountDownLatch(1);
        final CountDownLatch closeCalled = new CountDownLatch(1);
        final CountDownLatch connectFinished = new CountDownLatch(1);
        private volatile boolean closed;

        @Override
        public void connect(SocketAddress endpoint, int timeout) throws IOException {
            connectEntered.countDown();
            try {
                if (!closeCalled.await(2, TimeUnit.SECONDS)) {
                    throw new SocketException("test connection was not cancelled");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SocketException("test connection interrupted");
            } finally {
                connectFinished.countDown();
            }
            throw new SocketException("socket closed by disconnect");
        }

        @Override
        public void close() {
            closed = true;
            closeCalled.countDown();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }
}
