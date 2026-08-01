package com.bg7yoz.ft8cn.flex;
/**
 * 简单封装的Tcp类，用于Flex的命令操作
 * @author BGY70Z
 * @date 2023-03-20
 */

import android.util.Log;

import com.bg7yoz.ft8cn.util.BoundedSerialExecutor;
import com.bg7yoz.ft8cn.util.SubmissionResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RadioTcpClient {
    private static final String TAG = "RadioTcpClient";
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static volatile RadioTcpClient radioTcpClient = null;
    private volatile String ip;
    private volatile int port;
    public static final int MAX_BUFFER_SIZE=1024 * 32;

    private final BoundedSerialExecutor sendByteThreadPool;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong droppedSendCount = new AtomicLong();
    private final SocketFactory socketFactory;

    interface SocketFactory {
        Socket create() throws IOException;
    }

    public RadioTcpClient() {
        this(Socket::new, new BoundedSerialExecutor(256));
    }

    RadioTcpClient(SocketFactory socketFactory) {
        this(socketFactory, new BoundedSerialExecutor(256));
    }

    RadioTcpClient(SocketFactory socketFactory, BoundedSerialExecutor sendByteThreadPool) {
        this.socketFactory = socketFactory;
        this.sendByteThreadPool = sendByteThreadPool;
    }

    public static RadioTcpClient getInstance() {
        if (radioTcpClient == null) {
            synchronized (RadioTcpClient.class) {
                if (radioTcpClient == null) {
                    radioTcpClient = new RadioTcpClient();
                }
            }
        }
        return radioTcpClient;
    }

    private SocketThread mSocketThread;
    private volatile OnDataReceiveListener onDataReceiveListener = null;

    public boolean isConnect() {
        SocketThread session = mSocketThread;
        return session != null && session.isConnected() && isCurrent(session);
    }

    public synchronized void connect(String ip, int port) {
        disconnect();
        this.ip = ip;
        this.port = port;
        SocketThread session = new SocketThread(ip, port, generation.incrementAndGet());
        mSocketThread = session;
        session.start();
    }

    public synchronized void disconnect() {
        SocketThread session = mSocketThread;
        mSocketThread = null;
        generation.incrementAndGet();
        sendByteThreadPool.cancelPending();
        if (session != null) {
            session.stopSession();
        }
    }

    private boolean isCurrent(SocketThread session) {
        synchronized (this) {
            return mSocketThread == session
                    && generation.get() == session.generation
                    && !session.stopRequested;
        }
    }

    private void notifyConnectSuccess(SocketThread session) {
        OnDataReceiveListener listener;
        synchronized (this) {
            if (!isCurrent(session)) return;
            listener = onDataReceiveListener;
        }
        if (listener != null) listener.onConnectSuccess();
    }

    private void notifyConnectFail(SocketThread session) {
        OnDataReceiveListener listener;
        synchronized (this) {
            if (!isCurrent(session)) return;
            listener = onDataReceiveListener;
        }
        if (listener != null) listener.onConnectFail();
    }

    private void notifyData(SocketThread session, byte[] data) {
        OnDataReceiveListener listener;
        synchronized (this) {
            if (!isCurrent(session)) return;
            listener = onDataReceiveListener;
        }
        if (listener != null) listener.onDataReceive(data);
    }

    private class SocketThread extends Thread {
        private final String targetIp;
        private final int targetPort;
        private final long generation;
        private final AtomicBoolean closeNotified = new AtomicBoolean();
        private volatile boolean stopRequested;
        private volatile Socket socket;
        private volatile InputStream inputStream;
        private volatile OutputStream outputStream;

        private SocketThread(String targetIp, int targetPort, long generation) {
            this.targetIp = targetIp;
            this.targetPort = targetPort;
            this.generation = generation;
        }

        private boolean isConnected() {
            Socket currentSocket = socket;
            return currentSocket != null && currentSocket.isConnected() && !currentSocket.isClosed();
        }

        @Override
        public void run() {
            Log.d(TAG, "TcpSocketThread start...");
            try {
                Socket connectedSocket = socketFactory.create();
                synchronized (RadioTcpClient.this) {
                    if (!isCurrent(this)) {
                        closeResources(connectedSocket, null, null);
                        return;
                    }
                    socket = connectedSocket;
                }
                // Publish the socket before connect: disconnect() can therefore close an
                // in-flight connection instead of waiting for the platform timeout.
                connectedSocket.connect(new InetSocketAddress(targetIp, targetPort),
                        CONNECT_TIMEOUT_MILLIS);
                synchronized (RadioTcpClient.this) {
                    if (!isCurrent(this) || socket != connectedSocket) {
                        closeResources(connectedSocket, null, null);
                        return;
                    }
                    outputStream = connectedSocket.getOutputStream();
                    inputStream = connectedSocket.getInputStream();
                }
                notifyConnectSuccess(this);
            } catch (SocketException e) {
                Log.e(TAG,"TCP Connection exception:"+e.getMessage());
                notifyConnectFail(this);
                finishConnection(false);
                return;
            } catch (IOException e) {
                Log.e(TAG, "SocketThread connect io exception = " + e.getMessage());
                notifyConnectFail(this);
                finishConnection(false);
                return;
            }

            try {
                while (isCurrent(this) && !isInterrupted()) {
                    byte[] buffer = new byte[MAX_BUFFER_SIZE];
                    InputStream input = inputStream;
                    if (input == null) return;
                    int size = input.read(buffer);
                    if (size > 0) {
                        notifyData(this, Arrays.copyOf(buffer, size));
                    } else if (size == -1) {
                        finishConnection(true);
                        return;
                    }
                }
            } catch (SocketException e) {
                Log.e(TAG,"TCP Connection exception:"+e.getMessage());
                finishConnection(!stopRequested);
            } catch (IOException e) {
                Log.e(TAG, "SocketThread read io exception = " + e.getMessage());
                finishConnection(!stopRequested);
            } finally {
                closeResources(socket, inputStream, outputStream);
            }
        }

        private void stopSession() {
            stopRequested = true;
            closeResources(socket, inputStream, outputStream);
            interrupt();
        }

        private void finishConnection(boolean notify) {
            if (!closeNotified.compareAndSet(false, true)) return;
            stopRequested = true;
            Socket oldSocket = socket;
            InputStream oldInput = inputStream;
            OutputStream oldOutput = outputStream;
            closeResources(oldSocket, oldInput, oldOutput);
            OnDataReceiveListener listener = null;
            synchronized (RadioTcpClient.this) {
                boolean current = mSocketThread == this
                        && RadioTcpClient.this.generation.get() == generation;
                if (current) {
                    if (notify) listener = onDataReceiveListener;
                    mSocketThread = null;
                    RadioTcpClient.this.generation.incrementAndGet();
                }
            }
            if (listener != null) listener.onConnectionClosed();
        }
    }

    private void closeResources(Socket socket, InputStream inputStream,
                                OutputStream outputStream) {
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException e) {
            Log.d(TAG, "TCP output close exception: " + e.getMessage());
        }
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException e) {
            Log.d(TAG, "TCP input close exception: " + e.getMessage());
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            Log.d(TAG, "TCP socket close exception: " + e.getMessage());
        }
    }

    /**
     * send byte[] cmd
     * Exception : android.os.NetworkOnMainThreadException
     */
    public SubmissionResult sendByte(final byte[] mBuffer) {
        if (mBuffer == null) return SubmissionResult.INVALID_ARGUMENT;
        SocketThread session;
        synchronized (this) {
            session = mSocketThread;
            if (session == null || !session.isConnected() || !isCurrent(session)) {
                return SubmissionResult.SESSION_INACTIVE;
            }
        }
        // The session is captured before enqueueing; a later reconnect cannot
        // redirect this command to the new OutputStream.
        try {
            sendByteThreadPool.submit(new SendByteRunnable(this, session, mBuffer));
            return SubmissionResult.ENQUEUED;
        } catch (RejectedExecutionException rejected) {
            long dropped = droppedSendCount.incrementAndGet();
            Log.w(TAG, "TCP send queue rejected command; dropped=" + dropped);
            return SubmissionResult.REJECTED;
        }
    }

    private static class SendByteRunnable implements Runnable{
        private final RadioTcpClient client;
        private final SocketThread session;
        private final byte[] mBuffer;

        public SendByteRunnable(RadioTcpClient client, SocketThread session, byte[] mBuffer) {
            this.client = client;
            this.session = session;
            this.mBuffer = Arrays.copyOf(mBuffer, mBuffer.length);
        }

        @Override
        public void run() {
            if (!client.isCurrent(session)) return;
            try {
                OutputStream outputStream = session.outputStream;
                if (outputStream != null) {
                    outputStream.write(mBuffer);
                    outputStream.flush();
                }
            } catch (IOException e) {
                if (client.isCurrent(session)) {
                    Log.e(TAG, "TCP send exception: " + e.getMessage());
                    session.finishConnection(true);
                }
            }
        }
    }

    public interface OnDataReceiveListener {
        void onConnectSuccess();
        void onConnectFail();
        void onDataReceive(byte[] buffer);
        void onConnectionClosed();
    }

    public void setOnDataReceiveListener(OnDataReceiveListener dataReceiveListener) {
        onDataReceiveListener = dataReceiveListener;
    }

    long getDroppedSendCount() {
        return droppedSendCount.get();
    }

    public String getIp() { return ip; }
    public int getPort() { return port; }
}
