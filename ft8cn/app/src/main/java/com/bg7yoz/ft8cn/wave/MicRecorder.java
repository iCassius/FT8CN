package com.bg7yoz.ft8cn.wave;
/**
 * 使用Mic录音的操作。
 * @author BGY70Z
 * @date 2023-03-20
 * 已优化：使用可重用缓冲区减少GC压力。
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.ui.ToastMessage;

public class MicRecorder {
    private static final String TAG = "MicRecorder";
    private int bufferSize = 0;//最小缓冲区大小
    private static final int sampleRateInHz = 12000;//采样率
    private static final int channelConfig = AudioFormat.CHANNEL_IN_MONO; //单声道
    private static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //量化位数

    private AudioRecord audioRecord = null;//AudioRecord对象
    private boolean isRunning = false;//是否处于录音的状态。
    private OnDataListener onDataListener;
    private float[] reusableFloatBuffer; // 优化：重用缓冲区

    public interface OnDataListener{
        void onDataReceived(float[] data,int len);
    }

    @SuppressLint("MissingPermission")
    public MicRecorder(){
        //计算最小缓冲区
        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        initAudioRecord();
    }

    @SuppressLint("MissingPermission")
    private void initAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception ignored) {}
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz
                , channelConfig, audioFormat, bufferSize);//创建AudioRecorder对象
    }

    public void start(){
        if (isRunning) return;

        Context context = GeneralVariables.getMainContext();
        //未授予录音权限时不能启动 microphone 类型前台服务（Android 14+ 直接 SecurityException 闪退），
        //等 MainActivity 授权回调里重启录音
        if (context != null
                && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "start: RECORD_AUDIO not granted, skip recording until permission granted.");
            return;
        }
        if (context != null) {
            Intent intent = new Intent(context, AudioForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }

        // Wait a small bit to ensure service is foreground on Android 14
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}

        // Always re-initialize AudioRecord when starting to ensure correct state on Android 14
        initAudioRecord();

        final short[] buffer = new short[bufferSize / 2];
        try {
            audioRecord.startRecording();//开始录音
        }catch (Exception e){
            ToastMessage.show(String.format(GeneralVariables.getStringFromResource(
                    R.string.recorder_cannot_record),e.getMessage()));
            Log.d(TAG, "startRecord: "+e.getMessage() );
        }

        isRunning = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    //判断是否处于录音状态，state!=3，说明没有处于录音的状态
                    if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        isRunning = false;
                        Log.d(TAG, String.format("录音失败，状态码：%d", audioRecord.getRecordingState()));
                        break;
                    }

                    //读录音的数据
                    int bufferReadResult = audioRecord.read(buffer, 0, buffer.length);

                    if (bufferReadResult > 0) {
                        // 优化：仅在必要时扩容或初始化重用缓冲区
                        if (reusableFloatBuffer == null || reusableFloatBuffer.length < bufferReadResult) {
                            reusableFloatBuffer = new float[bufferReadResult];
                        }
                        
                        float sum = 0;
                        for (int i = 0; i < bufferReadResult; i++) {
                            reusableFloatBuffer[i] = buffer[i] / 32768.0f;
                            sum += Math.abs(reusableFloatBuffer[i]);
                        }

                        if (sum == 0) {
                            Log.w(TAG, "run: Received SILENT data (all zeros)");
                        }

                        if (onDataListener != null) {
                            onDataListener.onDataReceived(reusableFloatBuffer, bufferReadResult);
                        }
                    }
                }
                try {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    /**
     * 停止录音。当录音停止后，监听列表中的监听器全部删除。
     */
    public void stopRecord() {
        isRunning = false;
        Context context = GeneralVariables.getMainContext();
        if (context != null) {
            context.stopService(new Intent(context, AudioForegroundService.class));
        }
    }

    public OnDataListener getOnDataListener() {
        return onDataListener;
    }

    public void setOnDataListener(OnDataListener onDataListener) {
        this.onDataListener = onDataListener;
    }
}
