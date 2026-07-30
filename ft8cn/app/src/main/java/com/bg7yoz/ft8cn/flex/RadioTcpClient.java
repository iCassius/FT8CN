package com.bg7yoz.ft8cn.flex;
/**
 * 简单封装的Tcp类，用于Flex的命令操作
 * @author BGY70Z
 * @date 2023-03-20
 */

import android.util.Log;

import com.bg7yoz.ft8cn.util.BoundedSerialExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;

public class RadioTcpClient {
    private static final String TAG = "RadioTcpClient";
    private static RadioTcpClient radioTcpClient = null;
    private volatile String ip;
    private volatile int port;
    public static final int MAX_BUFFER_SIZE=1024 * 32;

    private final BoundedSerialExecutor sendByteThreadPool = new BoundedSerialExecutor(256);

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

    private volatile Socket mSocket;
    private volatile OutputStream mOutputStream;
    private volatile InputStream mInputStream;

    private SocketThread mSocketThread;
    private volatile boolean isStop = false;//thread flag

    private OnDataReceiveListener onDataReceiveListener = null;

    public boolean isConnect() {
        Socket socket = mSocket;
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized void connect(String ip, int port) {
        disconnect();
        this.ip = ip;
        this.port = port;
        isStop = false;
        mSocketThread = new SocketThread(ip, port);
        mSocketThread.start();
    }

    public synchronized void disconnect() {
        isStop = true;
        closeResources(mSocket, mInputStream, mOutputStream);
        mSocket = null;
        mInputStream = null;
        mOutputStream = null;
        if (mSocketThread != null) {
            mSocketThread.interrupt();//not intime destory thread,so need a flag
        }
    }

    private void closeResources(Socket socket, InputStream inputStream,
                                OutputStream outputStream) {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            Log.d(TAG, "TCP output close exception: " + e.getMessage());
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            Log.d(TAG, "TCP input close exception: " + e.getMessage());
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.d(TAG, "TCP socket close exception: " + e.getMessage());
        }
    }

    private class SocketThread extends Thread {
        private final String targetIp;
        private final int targetPort;
        private boolean closeNotified;

        private SocketThread(String targetIp, int targetPort) {
            this.targetIp = targetIp;
            this.targetPort = targetPort;
        }

        @Override
        public void run() {
            Log.d(TAG, "TcpSocketThread start...");
            Socket socket = null;
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                InetAddress ipAddress = InetAddress.getByName(targetIp);
                socket = new Socket(ipAddress, targetPort);
                synchronized (RadioTcpClient.this) {
                    if (isStop || mSocketThread != this) {
                        closeResources(socket, inputStream, outputStream);
                        return;
                    }
                    mSocket = socket;
                }
                if (!isStop && isConnect()) {
                    outputStream = socket.getOutputStream();
                    inputStream = socket.getInputStream();
                    synchronized (RadioTcpClient.this) {
                        mOutputStream = outputStream;
                        mInputStream = inputStream;
                    }
                    connectSuccess();
                } else {
                    connectFail();
                    closeResources(socket, inputStream, outputStream);
                    return;
                }
            } catch (SocketException e) {
                Log.e(TAG,"TCP Connection exception:"+e.getMessage());
                closeResources(socket, inputStream, outputStream);
                return;
            } catch (IOException e) {
                connectFail();
                Log.e(TAG, "SocketThread connect io exception = " + e.getMessage());
                closeResources(socket, inputStream, outputStream);
                return;
            }

            while (isConnect() && !isStop && !isInterrupted()) {
                try {
                    byte[] buffer = new byte[MAX_BUFFER_SIZE];
                    if (inputStream == null) {
                        return;
                    }
                    int size = inputStream.read(buffer);//null data -1 ,
                    if (size > 0) {
                        if (onDataReceiveListener != null) {
                            byte[] temp = Arrays.copyOf(buffer, size);
                            onDataReceiveListener.onDataReceive(temp);
                        }
                    } else if (size == -1) {
                        finishConnection(socket, inputStream, outputStream, true);
                        return;
                    }
                } catch (SocketException e) {
                    Log.e(TAG,"Tcp Connection exception:"+e.getMessage());
                    finishConnection(socket, inputStream, outputStream, !isStop);
                    return;
                } catch (IOException e) {
                    Log.e(TAG, "SocketThread read io exception = " + e.getMessage());
                    finishConnection(socket, inputStream, outputStream, !isStop);
                    return;
                }
            }
            closeResources(socket, inputStream, outputStream);
        }

        private void finishConnection(Socket socket, InputStream inputStream,
                                       OutputStream outputStream, boolean notify) {
            synchronized (this) {
                if (notify && closeNotified) {
                    return;
                }
                if (notify) {
                    closeNotified = true;
                }
            }
            closeResources(socket, inputStream, outputStream);
            boolean currentSession;
            synchronized (RadioTcpClient.this) {
                currentSession = mSocketThread == this;
                if (mSocket == socket) {
                    mSocket = null;
                    mInputStream = null;
                    mOutputStream = null;
                }
            }
            if (notify && currentSession && onDataReceiveListener != null) {
                onDataReceiveListener.onConnectionClosed();
            }
        }
    }

    private void connectFail() {
        if (onDataReceiveListener != null) {
            onDataReceiveListener.onConnectFail();
        }
    }

    private void connectSuccess() {
        if (onDataReceiveListener != null) {
            onDataReceiveListener.onConnectSuccess();
        }
    }

    /**
     * send byte[] cmd
     * Exception : android.os.NetworkOnMainThreadException
     */
    public synchronized void sendByte(final byte[] mBuffer) {
        if (mBuffer == null) {
            return;
        }
        sendByteThreadPool.execute(new SendByteRunnable(this, mBuffer));
    }

    private static class SendByteRunnable implements Runnable{
        private final RadioTcpClient client;
        private final byte[] mBuffer;

        public SendByteRunnable(RadioTcpClient client, byte[] mBuffer) {
            this.client = client;
            this.mBuffer = Arrays.copyOf(mBuffer, mBuffer.length);
        }

        @Override
        public void run() {
            try {
                OutputStream outputStream = client.mOutputStream;
                if (outputStream != null) {
                    outputStream.write(mBuffer);
                    outputStream.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "TCP send exception: " + e.getMessage());
            }
        }
    }

    public interface OnDataReceiveListener {
        void onConnectSuccess();

        void onConnectFail();

        void onDataReceive(byte[] buffer);
        void onConnectionClosed();
    }

    public void setOnDataReceiveListener(
            OnDataReceiveListener dataReceiveListener) {
        onDataReceiveListener = dataReceiveListener;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
