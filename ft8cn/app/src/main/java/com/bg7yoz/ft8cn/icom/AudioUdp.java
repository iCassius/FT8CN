package com.bg7yoz.ft8cn.icom;
/**
 * 处理音频流的基本类。
 * @author BGY70Z
 * @date 2023-08-26
 */

import android.util.Log;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.util.SubmissionResult;

import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioUdp extends IcomUdpBase {
    private static final String TAG = "AudioUdp";

    public AudioUdp() {
        udpStyle = IcomUdpStyle.AudioUdp;
    }



    public SubmissionResult sendTxAudioData(float[] audioData){
        return SubmissionResult.SESSION_INACTIVE;
    }
    public void startTxAudio(){}
    public void stopTXAudio(){}
}
