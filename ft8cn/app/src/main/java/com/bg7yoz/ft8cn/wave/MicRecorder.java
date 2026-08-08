package com.bg7yoz.ft8cn.wave;

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
    private int bufferSize = 0;
    private static final int sampleRateInHz = 12000;
    private static final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord = null;
    private volatile boolean isRunning = false;
    private OnDataListener onDataListener;
    private float[] reusableFloatBuffer;

    public interface OnDataListener {
        void onDataReceived(float[] data, int len);
    }

    public MicRecorder() {
        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        if (bufferSize <= 0) {
            bufferSize = sampleRateInHz;
        }
    }

    @SuppressLint("MissingPermission")
    private synchronized boolean ensureAudioRecord() {
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            return true;
        }
        releaseAudioRecord();
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz,
                    channelConfig, audioFormat, bufferSize);
        } catch (Exception e) {
            Log.d(TAG, "ensureAudioRecord: " + e.getMessage());
            audioRecord = null;
        }
        return audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED;
    }

    private synchronized void releaseAudioRecord() {
        if (audioRecord == null) {
            return;
        }
        try {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
        } catch (Exception e) {
            Log.d(TAG, "releaseAudioRecord stop: " + e.getMessage());
        }
        try {
            audioRecord.release();
        } catch (Exception e) {
            Log.d(TAG, "releaseAudioRecord release: " + e.getMessage());
        }
        audioRecord = null;
    }

    private boolean startAudioForegroundService() {
        Context context = GeneralVariables.getMainContext();
        if (context == null) {
            return true;
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "start: RECORD_AUDIO not granted, skip recording until permission granted.");
            return false;
        }
        try {
            Intent intent = new Intent(context, AudioForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "startAudioForegroundService: " + e.getMessage());
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private boolean startRecordingInternal() {
        if (!startAudioForegroundService() || !ensureAudioRecord()) {
            return false;
        }

        try {
            audioRecord.startRecording();
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "startRecord: " + e.getMessage());
        }

        releaseAudioRecord();
        if (!ensureAudioRecord()) {
            return false;
        }

        try {
            audioRecord.startRecording();
            return audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
        } catch (Exception e) {
            Log.d(TAG, "startRecord retry: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean start() {
        if (isRunning) return true;

        if (!startRecordingInternal()) {
            ToastMessage.show(String.format(GeneralVariables.getStringFromResource(
                    R.string.recorder_cannot_record), "AudioRecord is not initialized"));
            Log.d(TAG, "startRecord: AudioRecord is not initialized");
            return false;
        }

        isRunning = true;
        final short[] buffer = new short[Math.max(1, bufferSize / 2)];

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    AudioRecord currentRecord = audioRecord;
                    if (currentRecord == null
                            || currentRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        isRunning = false;
                        Log.d(TAG, String.format("record failed, state:%d",
                                currentRecord == null ? -1 : currentRecord.getRecordingState()));
                        break;
                    }

                    int bufferReadResult = currentRecord.read(buffer, 0, buffer.length);
                    if (bufferReadResult <= 0) {
                        Log.d(TAG, "record loop read error: " + bufferReadResult);
                        if (bufferReadResult == AudioRecord.ERROR_DEAD_OBJECT
                                || bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION
                                || bufferReadResult == AudioRecord.ERROR_BAD_VALUE) {
                            isRunning = false;
                            break;
                        }
                        continue;
                    }

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
                releaseAudioRecord();
            }
        }, "FT8CN-MicRecorder").start();
        return true;
    }

    public synchronized void stopRecord() {
        isRunning = false;
        Context context = GeneralVariables.getMainContext();
        if (context != null) {
            context.stopService(new Intent(context, AudioForegroundService.class));
        }
        releaseAudioRecord();
    }

    public OnDataListener getOnDataListener() {
        return onDataListener;
    }

    public void setOnDataListener(OnDataListener onDataListener) {
        this.onDataListener = onDataListener;
    }
}
