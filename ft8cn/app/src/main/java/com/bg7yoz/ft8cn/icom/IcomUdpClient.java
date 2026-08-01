package com.bg7yoz.ft8cn.icom;
/**
 * 简单封装的udp协议处理
 *
 * @author BGY70Z
 * @date 2023-03-20
 */

import android.util.Log;

import com.bg7yoz.ft8cn.util.BoundedSerialExecutor;
import com.bg7yoz.ft8cn.util.SubmissionResult;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public class IcomUdpClient {
    private static final String TAG = "RadioUdpSocket";
    private final int MAX_BUFFER_SIZE = 1024 * 2;
    private volatile DatagramSocket sendSocket;
    private int localPort = -1;
    private volatile boolean activated = false;
    private volatile OnUdpEvents onUdpEvents = null;
    private final ExecutorService doReceiveThreadPool = Executors.newCachedThreadPool();
    private final BoundedSerialExecutor sendDataThreadPool;
    private final AtomicLong droppedSendCount = new AtomicLong();
    private long generation;

    public IcomUdpClient() {//本地端口随机
        this(-1, new BoundedSerialExecutor(256));
    }

    public IcomUdpClient(int localPort) {//如果localPort==-1，本地端口随机
        this(localPort, new BoundedSerialExecutor(256));
    }

    IcomUdpClient(int localPort, BoundedSerialExecutor sendDataThreadPool) {
        this.localPort = localPort;
        this.sendDataThreadPool = sendDataThreadPool;
    }

    public SubmissionResult sendData(byte[] data, String ip, int port) throws UnknownHostException {
        if (data == null) return SubmissionResult.INVALID_ARGUMENT;
        InetAddress address = InetAddress.getByName(ip);
        DatagramSocket socket;
        long session;
        synchronized (this) {
            if (!activated || sendSocket == null) return SubmissionResult.SESSION_INACTIVE;
            socket = sendSocket;
            session = generation;
        }
        try {
            sendDataThreadPool.submit(new SendDataRunnable(this, socket, session, address, data, port));
            return SubmissionResult.ENQUEUED;
        } catch (RejectedExecutionException rejected) {
            long dropped = droppedSendCount.incrementAndGet();
            Log.w(TAG, "UDP send queue rejected packet; dropped=" + dropped);
            return SubmissionResult.REJECTED;
        }
    }

    private static class SendDataRunnable implements Runnable {
        private final IcomUdpClient client;
        private final DatagramSocket socket;
        private final long generation;
        private final byte[] data;
        private final int port;
        private final InetAddress address;

        public SendDataRunnable(IcomUdpClient client, DatagramSocket socket, long generation,
                                InetAddress address, byte[] data, int port) {
            this.client = client;
            this.socket = socket;
            this.generation = generation;
            this.address = address;
            this.data = Arrays.copyOf(data, data.length);
            this.port = port;
        }

        @Override
        public void run() {
            if (!client.isCurrentSession(socket, generation)) return;
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            try {
                socket.send(packet);
            } catch (IOException e) {
                if (client.isCurrentSession(socket, generation)
                        && client.onUdpEvents != null) {
                    client.onUdpEvents.OnUdpSendIOException(e);
                }
            }
        }
    }

    public boolean isActivated() {
        return activated;
    }

    public synchronized void setActivated(boolean activated) throws SocketException {
        DatagramSocket oldSocket = sendSocket;
        generation++;
        this.activated = false;
        sendSocket = null;
        sendDataThreadPool.cancelPending();
        if (oldSocket != null) {
            oldSocket.close();
        }
        if (!activated) {
            return;
        }

        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        if (localPort != -1) {
            socket.bind(new InetSocketAddress(localPort));
        } else {
            socket.bind(new InetSocketAddress(0));
        }
        localPort = socket.getLocalPort();
        sendSocket = socket;
        this.activated = true;
        long session = generation;
        Log.e(TAG, "openUdpPort: " + socket.getLocalPort());
        doReceiveThreadPool.execute(new DoReceiveRunnable(this, socket, session));
    }

    private boolean isCurrentSession(DatagramSocket socket, long session) {
        synchronized (this) {
            return activated && generation == session && sendSocket == socket && !socket.isClosed();
        }
    }

    private void endReceive(DatagramSocket socket, long session) {
        synchronized (this) {
            if (sendSocket == socket && generation == session) {
                activated = false;
                sendSocket = null;
                generation++;
            }
        }
    }

    public void setOnUdpEvents(OnUdpEvents onUdpEvents) {
        this.onUdpEvents = onUdpEvents;
    }

    public int getLocalPort() {
        DatagramSocket socket = sendSocket;
        return socket == null ? 0 : socket.getLocalPort();
    }

    public String getLocalIp() {
        DatagramSocket socket = sendSocket;
        return socket == null ? "127.0.0.1" : socket.getLocalAddress().toString();
    }

    public DatagramSocket getSendSocket() {
        return sendSocket;
    }

    long getDroppedSendCount() {
        return droppedSendCount.get();
    }

    public static String byteToStr(byte[] data) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            s.append(String.format("%02x ", data[i] & 0xff));
        }
        return s.toString();
    }

    private static class DoReceiveRunnable implements Runnable {
        private final IcomUdpClient client;
        private final DatagramSocket socket;
        private final long generation;

        public DoReceiveRunnable(IcomUdpClient client, DatagramSocket socket, long generation) {
            this.client = client;
            this.socket = socket;
            this.generation = generation;
        }

        @Override
        public void run() {
            try {
                while (client.isCurrentSession(socket, generation)) {
                    byte[] data = new byte[client.MAX_BUFFER_SIZE];
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    try {
                        socket.receive(packet);
                        if (!client.isCurrentSession(socket, generation)) break;
                        if (client.onUdpEvents != null) {
                            byte[] temp = Arrays.copyOf(packet.getData(), packet.getLength());
                            client.onUdpEvents.OnReceiveData(socket, packet, temp);
                        }
                    } catch (IOException e) {
                        if (client.isCurrentSession(socket, generation)) {
                            Log.e(TAG, "receiveData: error:" + e.getMessage());
                        }
                        break;
                    }
                }
            } finally {
                socket.close();
                client.endReceive(socket, generation);
            }
            Log.e(TAG, "udpClient: is exit!");
        }
    }

    public interface OnUdpEvents {
        void OnReceiveData(DatagramSocket socket, DatagramPacket packet, byte[] data);

        void OnUdpSendIOException(IOException e);
    }
}
