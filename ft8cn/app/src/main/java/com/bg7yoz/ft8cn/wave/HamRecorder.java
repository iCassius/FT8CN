package com.bg7yoz.ft8cn.wave;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * 录音类。通过AudioRecord对象来实现录音。
 * HamRecorder录音的数据通过监听类GetVoiceData来实现。HamRecorder实例中有一个监听器列表onGetVoiceList。
 * 当有录音数据后，HamRecorder会触发监听器列表中各监听器的OnReceiveData回调。
 * 制作此类的目的，是防止FT8各录音时序因录音启动时间的问题，造成重叠创建录音对象或录音的时长达不到一个时序的时长（15秒）
 * <p>
 * @author BG7YOZ
 * @date 2022-05-31
 */

public class HamRecorder {
    private static final String TAG = "HamRecorder";
    //private int bufferSize = 0;//最小缓冲区大小
    private static final int sampleRateInHz = 12000;//采样率
    private static final int channelConfig = AudioFormat.CHANNEL_IN_MONO; //单声道
    //private static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //量化位数
    private static final int audioFormat = AudioFormat.ENCODING_PCM_FLOAT; //量化位数

    //private AudioRecord audioRecord = null;//AudioRecord对象
    private boolean isRunning = false;//是否处于录音的状态。

    //监听回调列表，在监听回调中获取数据。
    //CopyOnWriteArrayList：音频线程遍历的同时，定时器线程会增删监听器，
    //且一次性监听器会在回调中删除自己（普通ArrayList正向遍历时自删会跳过下一个监听器，导致丢数据）。
    private final CopyOnWriteArrayList<VoiceDataMonitor> voiceDataMonitorList = new CopyOnWriteArrayList<>();
    private OnVoiceMonitorChanged onVoiceMonitorChanged=null;

    private boolean isMicRecord=true;
    private MicRecorder micRecorder=new MicRecorder();


    public HamRecorder(OnVoiceMonitorChanged onVoiceMonitorChanged){
        this.onVoiceMonitorChanged=onVoiceMonitorChanged;
    }


    public void setDataFromMic(){
        isMicRecord=true;
        startRecord();
    }
    public void setDataFromLan(){
        isMicRecord=false;
        micRecorder.stopRecord();
    }

    /**
     * 当接收到音频数据，所要处理的事情
     * @param bufferLen 数据的长度
     * @param buffer 数据缓冲区
     */
    public void doOnWaveDataReceived(int bufferLen,float[] buffer){
        if (!isRunning) return;
        for (VoiceDataMonitor monitor : voiceDataMonitorList) {//迭代的是快照，回调中增删监听器不影响本次遍历
            if (monitor != null) {
                monitor.onHamRecord.OnReceiveData(buffer, bufferLen);
            }
        }

        //doDataMonitorChanged();
    }


    /**
     * 是否处于录音状态
     *
     * @return boolean，是否处于录音状态
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 开始录音，此方法使设备一直处于录音状态，录音数据的获取通过监听器类GetVoiceData来实现。
     * 录音对象在读取到数据（audioRecord.read）后，把监听器列表中的所有监听器的OnReceiveData回调都调用一次。
     * 录音的状态在isRecording中。
     */
    @SuppressLint("MissingPermission")
    public void startRecord() {
        if (isMicRecord){//如果是用MIC采集声音
            if (!micRecorder.start()) {
                isRunning = false;
                return;
            }
            micRecorder.setOnDataListener(new MicRecorder.OnDataListener() {
                @Override
                public void onDataReceived(float[] data, int len) {
                    doOnWaveDataReceived(len,data);
                }
            });
        }
        isRunning=true;

    }

    private void doDataMonitorChanged(){
        if (onVoiceMonitorChanged!=null){
            onVoiceMonitorChanged.onMonitorChanged(voiceDataMonitorList.size());
        }
    }
    /**
     * 删除数据监听器
     * @param monitor 数据监听器
     */
    public void deleteVoiceDataMonitor(VoiceDataMonitor monitor) {
        voiceDataMonitorList.remove(monitor);
        doDataMonitorChanged();
    }

    /**
     * 获取监听器的数量
     * @return 返回数量
     */
    public int getVoiceMonitorCount(){
        return voiceDataMonitorList.size();
    }

    /**
     * 获取监听器的列表
     * @return 监听器列表
     */
    public CopyOnWriteArrayList<VoiceDataMonitor> getVoiceDataMonitors(){
        return this.voiceDataMonitorList;
    }

    /**
     * 停止录音。当录音停止后，监听列表中的监听器全部删除。
     */
    public void stopRecord() {
        micRecorder.stopRecord();
        isRunning = false;
    }

    /**
     * 获取录音数据的方法，通过加载数据监听器（VoiceDataMonitor）的方法实现。
     * 录音数据在OnGetVoiceDataDone回调中，当录音达到指定的时长（毫秒）触发。
     * 获取录音，是给录音对象加载一个监听器对象，在监听器的OnReceiveData回调中获取数据，当数据达到预期的数量时，
     * 触发OnGetVoiceDataDone回调。该回调动作在另一个线程中，要注意UI的处理。
     * 监听有两种模式：一次性、循环。
     * 一次性：获取数据后，此监听器自动删除，不再触发。
     * 循环，监听器始终存在，获取数据后，重新复位数据，进入下一次监听状态。直到录音停止，监听器才被删除。
     * duration毫秒
     *
     * @param duration         录音数据的时长（毫秒）
     * @param afterDoneRemove  获取录音后是否删除监听器，false：循环获取录音数据。
     * @param getVoiceDataDone 当录音数据达到指定的时长后，触发此回调
     */
    public VoiceDataMonitor getVoiceData(int duration, boolean afterDoneRemove, OnGetVoiceDataDone getVoiceDataDone) {
        if (isRunning) {
            if (afterDoneRemove) {
                //新的一次性录音窗口（解码周期）开始前，把上一个还没凑满的一次性窗口强制结算（不足补零）。
                //否则音频流一旦断流（发射期间、网络丢包），旧窗口会吞掉下一个周期的音频，
                //解码数据与UTC时隙错位，且未完成的窗口会越积越多（每个持有720KB缓冲区）。
                for (VoiceDataMonitor monitor : voiceDataMonitorList) {
                    if (monitor.oneShot) {
                        monitor.forceComplete();
                    }
                }
            }
            VoiceDataMonitor dataMonitor = new VoiceDataMonitor(duration, this
                    , afterDoneRemove, getVoiceDataDone);
            dataMonitor.voiceDataMonitor = dataMonitor;//用于监听器删除自己用。
            voiceDataMonitorList.add(dataMonitor);
            doDataMonitorChanged();
            return dataMonitor;
        } else {
            return null;
        }
    }

    /**
     * 监听器类，用于录音数据的获取。
     * 当监听类，需要设定录音的时长（毫秒），当达到指定的时长后，会产生一个OnGetVoiceDataDone回调，在此回调中，可以获得
     * 该时长的录音数据。可以设定此监听是一次性的（afterDoneRemove=true）,还是循环往复的（afterDoneRemove=false）。
     * 一次性的，就是监听达到指定时长后，就不继续监听了，录音实例会把该监听删除。
     * 循环往复，就是监听到指定时长后，复位，继续重新监听。此模式方便形成波表数据。
     */
    static class VoiceDataMonitor {
        private final String TAG = "GetVoiceData";
        private final float[] voiceData;//录音数据。大小由时长、采样率、采样位决定的。
        private int dataCount;//计数器，当前数据的获取量
        private boolean done = false;//一次性监听器是否已经结算（正常凑满或被强制结算）
        public final boolean oneShot;//是否是一次性监听器（afterDoneRemove=true）
        private final HamRecorder hamRecorder;
        private final OnGetVoiceDataDone onGetVoiceDataDone;

        //onHamRecord是当录音对象有数据时触发的回调，通过该回调填充voiceData缓冲区，当缓冲区满时，触发OnGetVoiceDataDone回调。
        public OnHamRecord onHamRecord;
        //getVoiceData是本监听器的地址，用于在录音对象的监听列表中删除本监听器。
        // 在GetVoiceData构建后，注意！！！一定要对该变量赋值！否则无法删除本监听器。
        public VoiceDataMonitor voiceDataMonitor = null;

        /**
         * 监听类，用于录音数据的获取
         * GetVoiceData类的构建方法。此类是用于添加到录音类HamRecorder中onGetVoiceList，当有录音数据返回时，产生回调。
         * 此类的目的就是录音时，可以有多个对象从录音中获取数据，而不产生冲突。
         *
         * @param duration           获取录音数据的时长（毫秒）。
         * @param hamRecorder        录音类的实例。方便删除本监听器等操作。
         * @param afterDoneRemove    当达到录音的时长后，是否移除本监听实例，true：移除，false：不移除，循环监听
         * @param onGetVoiceDataDone 达到录音的时长后，触发此回调。为了防止占用太多录音的时间，此回调在另一个线程。
         */
        public VoiceDataMonitor(int duration, HamRecorder hamRecorder, boolean afterDoneRemove
                , OnGetVoiceDataDone onGetVoiceDataDone) {
            //时长，毫秒
            //宿主对象，方便用词对象调用删除数据获取动作列表中的本实例

            dataCount = 0;//当前的数据获取量
            oneShot = afterDoneRemove;
            this.hamRecorder = hamRecorder;
            this.onGetVoiceDataDone = onGetVoiceDataDone;
            //生成预期大小中的数据缓冲区。
            voiceData = new float[duration * HamRecorder.sampleRateInHz  / 1000];

            //当有录音数据时触发的回调函数。填充动作与强制结算共用一把锁，防止并发。
            onHamRecord = new OnHamRecord() {
                @Override
                public void OnReceiveData(float[] data, int size) {
                    receiveData(data, size);
                }
            };

        }

        private synchronized void receiveData(float[] data, int size) {
            if (done) return;//一次性监听器已经结算过，不再接收数据
            int remainingSize = size + dataCount - voiceData.length;//如果大于0,就是剩余的数据量，

            for (int i = 0; (i < size) && (dataCount < voiceData.length); i++) {
                voiceData[dataCount] = data[i];//把录音缓冲区的数据搬运到本监听器中来
                dataCount++;
            }

            if (dataCount >= (voiceData.length)) {//当数据量达到所需要的。发起回调。
                if (oneShot) {//如果是一次性的获取数据，则在录音对象中的监听列表中删除此监听回调。
                    done = true;
                    onGetVoiceDataDone.onGetDone(voiceData);
                    hamRecorder.deleteVoiceDataMonitor(voiceDataMonitor);
                } else {
                    onGetVoiceDataDone.onGetDone(voiceData);
                    dataCount = 0;//如果是循环录音，则复位计数器。
                    if (remainingSize > 0) {//把剩余的数据补发到后续事件上
                        float[] remainingData = new float[remainingSize];
                        System.arraycopy(data, size - remainingSize, remainingData, 0, remainingSize);
                        receiveData(remainingData, remainingSize);
                    }
                }
            }
        }

        /**
         * 强制结算：把没凑满的部分补零，立刻触发onGetDone，并把自己从监听列表中删除。
         * 用于新录音周期开始时结算上一个周期，保证录音窗口始终与UTC时隙对齐。
         * 只对一次性监听器有效。
         */
        public synchronized void forceComplete() {
            if (!oneShot || done) return;
            done = true;
            if (dataCount < voiceData.length) {
                //缺口明显（超过5%）才提示，正常周期切换时只差零点几秒的尾巴
                if (voiceData.length - dataCount > voiceData.length / 20) {
                    Log.w(TAG, String.format("录音窗口强制结算：实收 %d/%d 采样，缺口补零"
                            , dataCount, voiceData.length));
                }
                Arrays.fill(voiceData, dataCount, voiceData.length, 0f);
            }
            onGetVoiceDataDone.onGetDone(voiceData);
            hamRecorder.deleteVoiceDataMonitor(voiceDataMonitor);
        }

    }

    /**
     * 类方法，把数据保存到文件中去，是临时文件名。
     * @param data 数据
     * @return 返回生成的临时文件名。
     */
    public static String saveDataToFile(byte[] data) {
        String audioFileName = null;
        File recordingFile;
        try {
            //生成临时文件名
            recordingFile = File.createTempFile("Audio", ".wav", null);
            audioFileName = recordingFile.getPath();

            //数据流文件
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(audioFileName)));
            //写Wav文件头
            new WriteWavHeader(data.length, sampleRateInHz, channelConfig, audioFormat).writeHeader(dos);
            for (int i = 0; i < data.length; i++) {
                dos.write(data[i]);
            }
            Log.d(TAG, String.format("生成文件结束(%d字节，%.2f秒)，文件：%s", data.length + 44
                    , ((float) data.length / 2 / sampleRateInHz), audioFileName));
            dos.close();//关闭文件流


        } catch (IOException e) {
            Log.e(TAG, String.format("生成临时文件出错！%s", e.getMessage()));
        }

        return audioFileName;
    }

    /**
     * 把原始的声音数据转换成16位的数组数据。
     * @param buffer 原始的声音数据(8位)
     * @return 返回16位的int格式数组
     */
    public static int[] byteDataTo16BitData(byte[] buffer){
        int[] data=new int[buffer.length /2];
        for (int i = 0; i < buffer.length/2; i++) {
            int  res = (buffer[i*2] & 0x000000FF) | (((int) buffer[i*2+1]) << 8);
            data[i]=res;
        }
        return data;
    }

    /**
     * 把原始的声音数据转换成浮点数组数据
     * @param bytes 原始的声音数据（float）
     * @return 转换成float数组
     */
    public static float[] getFloatFromBytes(byte[] bytes) {
        float[] floats = new float[bytes.length / 4];
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        for (int i = 0; i < floats.length; i++) {
            try {
                floats[i] = dis.readFloat();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
        try {
            dis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return floats;
    }
}
