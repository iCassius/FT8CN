package com.bg7yoz.ft8cn.connector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.flex.FlexCommand;
import com.bg7yoz.ft8cn.flex.FlexMeterInfos;
import com.bg7yoz.ft8cn.flex.FlexMeterList;
import com.bg7yoz.ft8cn.flex.FlexRadio;
import com.bg7yoz.ft8cn.flex.RadioTcpClient;
import com.bg7yoz.ft8cn.flex.VITA;
import com.bg7yoz.ft8cn.rigs.BaseRig;
import com.bg7yoz.ft8cn.ui.ToastMessage;
import com.bg7yoz.ft8cn.x6100.X6100Meters;
import com.bg7yoz.ft8cn.x6100.X6100Radio;
import com.bg7yoz.ft8cn.util.SubmissionResult;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网络连接方式连接xiegu ft8cns
 * @author BGY70Z
 * @date 2023-12-01
 */
public class X6100Connector extends BaseRigConnector {

    public interface OnWaveDataReceived{
        void OnDataReceived(int bufferLen,float[] buffer);
    }
    //public int maxRfPower;
    //public int maxTunePower;

    private static final String TAG = "X6100Connector";

    private X6100Radio xieguRadio;

    private OnWaveDataReceived onWaveDataReceived;

    private BaseRig baseRig;
    private volatile boolean streamIsOn =false;
    private static final int DEFAULT_STREAM_INIT_MAX_ATTEMPTS = 12;
    private static final long DEFAULT_STREAM_INIT_RETRY_DELAY_MS = 300L;
    private final int streamInitMaxAttempts;
    private final long streamInitRetryDelayMs;
    private final AtomicLong sessionGeneration = new AtomicLong();
    private final Object streamInitLock = new Object();
    private volatile Thread streamInitThread;

    public float maxTXPower=10.0f;
    public MutableLiveData<Float> mutableMaxTxPower = new MutableLiveData<>();


    public X6100Connector(Context context, X6100Radio xiegRadio, int controlMode) {
        this(context, xiegRadio, controlMode,
                DEFAULT_STREAM_INIT_MAX_ATTEMPTS, DEFAULT_STREAM_INIT_RETRY_DELAY_MS);
    }

    X6100Connector(Context context, X6100Radio xiegRadio, int controlMode,
                   int streamInitMaxAttempts, long streamInitRetryDelayMs) {
        super(controlMode);
        this.xieguRadio = xiegRadio;
        if (streamInitMaxAttempts <= 0 || streamInitRetryDelayMs < 0) {
            throw new IllegalArgumentException("invalid stream initialization retry policy");
        }
        this.streamInitMaxAttempts = streamInitMaxAttempts;
        this.streamInitRetryDelayMs = streamInitRetryDelayMs;
        setXieguRadioInterface();

    }

    public static short[] byteDataTo16BitData(byte[] buffer){
        short[] data=new short[buffer.length /2];
        for (int i = 0; i < buffer.length/2; i++) {
            short  res = (short) ((buffer[i*2+1] & 0x00FF) | (((short) buffer[i*2]) << 8));
            data[i]=res;
        }
        return data;
    }

    /**
     * 把原始的声音数据转换成16位的数组数据。
     * @param buffer 原始的声音数据(8位)
     * @return 返回16位的int格式数组
     */
    private float[] byteDataToFloatData(byte[] buffer){
        float[] data=new float[buffer.length /2];
        for (int i = 0; i < buffer.length/2; i++) {
            int  res = (buffer[i*2] & 0x000000FF) | (((int) buffer[i*2+1]) << 8);
            data[i]=res/32768.0f;
        }
        return data;
    }
    private void setXieguRadioInterface() {
        xieguRadio.setOnReceiveStreamData(new X6100Radio.OnReceiveStreamData() {
            @Override
            public void onReceiveAudio(byte[] data) {
                if (onWaveDataReceived!=null){
                    float[] waveFloat = byteDataToFloatData(data);
                    onWaveDataReceived.OnDataReceived(waveFloat.length,waveFloat);
                }
            }

            @Override
            public void onReceiveIQ(byte[] data) {

            }

            @Override
            public void onReceiveFFT(VITA vita) {

            }

            @Override
            public void onReceiveMeter(X6100Meters meters) {
                maxTXPower= meters.max_power;
                mutableMaxTxPower.postValue(maxTXPower);
            }

            @Override
            public void onReceiveUnKnow(byte[] data) {

            }
        });


        //当有命令返回值时的事件
        xieguRadio.setOnCommandListener(new X6100Radio.OnCommandListener() {
            @Override
            public void onResponse(X6100Radio.XieguResponse response) {
                Log.d(TAG, String.format("onResponse(%s): %s"
                        ,response.xieguCommand.toString(),response.rawData ));
                if (response.xieguCommand == X6100Radio.XieguCommand.STREAM){
                   if (response.resultContent.toUpperCase().contains("PORT=")){//说明流端口打开了
                        streamIsOn =true;
                   }
                }

                if (response.resultCode!=0) {//只显示失败的命令
                    ToastMessage.show(response.resultContent);
                    Log.e(TAG, "onResponse: "+response.resultContent);
                }

            }
        });

        //当有状态信息接收到时
        xieguRadio.setOnStatusListener(new X6100Radio.OnStatusListener() {
            @Override
            public void onStatus(X6100Radio.XieguResponse response) {
                //显示状态消息
                if (response.resultCode == 0){//说明是电台状态变化了
                    String status[] = response.resultContent.split(" ");
                    for (int i = 0; i < status.length; i++) {
                        if (status[i].startsWith("active_freq")){//找出频率状态，设置频率
                            String temp[]=status[i].split("=");
                            if (baseRig != null) {
                                baseRig.setFreq(Long.parseLong(temp[1].trim()));
                            }
                        }
                    }
                }
                Log.d(TAG, "onStatus: "+response.rawData );
            }
        });



        xieguRadio.setOnTcpConnectStatus(new X6100Radio.OnTcpConnectStatus() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onConnectSuccess(RadioTcpClient tcpClient) {
                ToastMessage.show(String.format(GeneralVariables.getStringFromResource(R.string.init_flex_operation)
                        ,xieguRadio.getModelName()));
                startStreamInitialization(sessionGeneration.get());
            }

            @Override
            public void onConnectFail(RadioTcpClient tcpClient) {
                ToastMessage.show(String.format(GeneralVariables.getStringFromResource
                        (R.string.xiegu_connect_failed),xieguRadio.getModelName()));
            }

            @Override
            public void onConnectionClosed(RadioTcpClient tcpClient) {
                endSession();
                // Use the connector bridge so EOF is safe even before MainViewModel has
                // attached the BaseRig during connectRig()/setBaseRig().
                getOnConnectorStateChanged().onDisconnected();
            }
        });

    }


    @Override
    public void sendData(byte[] data) {
        submitData(data);
    }

    @Override
    public SubmissionResult submitData(byte[] data) {
        SubmissionResult result = xieguRadio.submitData(data);
        reportOperationSubmission("XieGu data", result);
        return result;
    }


    @Override
    public void setPttOn(boolean on) {
        submitPttOn(on);
    }

    @Override
    public SubmissionResult submitPttOn(boolean on) {
        SubmissionResult result = xieguRadio.commandPTTOnOff(on);
        if (result.isEnqueued()) {
            xieguRadio.isPttOn = on;
        }
        reportOperationSubmission("XieGu PTT", result);
        return result;
    }

    @Override
    public void setPttOn(byte[] command) {
    }

    @Override
    public void sendWaveData(float[] data) {
        xieguRadio.sendWaveData(data);
    }


    public void setMaxTXPower(int power){
        maxTXPower=power;
        mutableMaxTxPower.postValue(maxTXPower);
        GeneralVariables.flexMaxRfPower=power;
        xieguRadio.commandSetTxPower(power);//设置发射功率

    }

    //传送a91数据包的方式
    @Override
    public void sendFt8A91(byte[] a91,float baseFreq){
        Log.d(TAG,String.format("A91:%s", BaseRig.byteToStr(a91)));
        //xieguRadio.commandSendA91(a91,GeneralVariables.volumePercent,baseFreq);
        xieguRadio.commandSendA91(a91,0.95f,baseFreq);
    }

    @Override
    public void setRFVolume(int volume) {
        xieguRadio.commandSetTxVol(volume);
    }

    @Override
    public void connect() {
        super.connect();
        beginSession();
        xieguRadio.openAudio();
        xieguRadio.connect();
        xieguRadio.openStreamPort();
    }

    @Override
    public void disconnect() {
        super.disconnect();
        endSession();
        xieguRadio.closeAudio();
        xieguRadio.closeStreamPort();
        xieguRadio.disConnect();
    }

    public OnWaveDataReceived getOnWaveDataReceived() {
        return onWaveDataReceived;
    }

    public void setOnWaveDataReceived(OnWaveDataReceived onWaveDataReceived) {
        this.onWaveDataReceived = onWaveDataReceived;
    }

    public BaseRig getBaseRig() {
        return baseRig;
    }

    public void setBaseRig(BaseRig baseRig) {
        this.baseRig = baseRig;
    }

    private void beginSession() {
        cancelStreamInitialization();
        sessionGeneration.incrementAndGet();
        streamIsOn = false;
    }

    private void endSession() {
        sessionGeneration.incrementAndGet();
        streamIsOn = false;
        cancelStreamInitialization();
    }

    private void cancelStreamInitialization() {
        Thread worker;
        synchronized (streamInitLock) {
            worker = streamInitThread;
            streamInitThread = null;
        }
        if (worker != null && worker != Thread.currentThread()) {
            worker.interrupt();
        }
    }

    private void startStreamInitialization(long generation) {
        Thread worker = new Thread(() -> {
            try {
                for (int attempt = 0; attempt < streamInitMaxAttempts; attempt++) {
                    if (!isCurrentStreamSession(generation) || streamIsOn) return;
                    xieguRadio.commandOpenStream();

                    long delay = Math.min(streamInitRetryDelayMs * (attempt + 1), 1500L);
                    if (delay > 0) {
                        Thread.sleep(delay);
                    }
                    if (!isCurrentStreamSession(generation) || streamIsOn) return;

                    // These subscriptions belong to the same live session as the open request.
                    xieguRadio.commandGetAudioInfo();
                    xieguRadio.commandSubAllMeter();
                }
                Log.w(TAG, "stream initialization stopped after bounded retries");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                synchronized (streamInitLock) {
                    if (streamInitThread == Thread.currentThread()) {
                        streamInitThread = null;
                    }
                }
            }
        }, "X6100-stream-init-" + generation);
        worker.setDaemon(true);

        Thread previous;
        synchronized (streamInitLock) {
            previous = streamInitThread;
            streamInitThread = worker;
        }
        if (previous != null && previous != Thread.currentThread()) {
            previous.interrupt();
        }
        worker.start();
    }

    private boolean isCurrentStreamSession(long generation) {
        return sessionGeneration.get() == generation
                && Thread.currentThread() == streamInitThread
                && xieguRadio.isConnect();
    }

    boolean isStreamInitializationRunningForTest() {
        Thread worker = streamInitThread;
        return worker != null && worker.isAlive();
    }

    @Override
    public boolean isConnected() {
        return xieguRadio.isConnect();
    }

    public X6100Radio getXieguRadio() {
        return xieguRadio;
    }
}
