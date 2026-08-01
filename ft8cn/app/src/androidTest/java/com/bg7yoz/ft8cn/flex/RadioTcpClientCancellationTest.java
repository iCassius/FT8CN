package com.bg7yoz.ft8cn.flex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class RadioTcpClientCancellationTest {
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
