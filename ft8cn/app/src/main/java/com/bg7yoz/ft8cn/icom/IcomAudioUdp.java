package com.bg7yoz.ft8cn.icom;
/**
 * 处理ICom的音频流，继承至AudioUdp。
 * @author BGY70Z
 * @date 2023-08-26
 */

import android.util.Log;

import com.bg7yoz.ft8cn.GeneralVariables;

import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class IcomAudioUdp extends AudioUdp {
    private static final String TAG = "IcomAudioUdp";


    private final ExecutorService doTXThreadPool =Executors.newCachedThreadPool();
    private final DoTXAudioRunnable doTXAudioRunnable=new DoTXAudioRunnable(this);


    @Override
    public void sendTxAudioData(float[] audioData) {
        if (audioData==null) return;

        short[] temp=new short[audioData.length];
        //传递过来的音频是LPCM,32 float，12000Hz
        //iCOM的音频格式是LPCM 16 Int，12000Hz
        //要做一下浮点到16位int的转换
        for (int i = 0; i < audioData.length; i++) {
            float x = audioData[i];
            if (x > 1.0)
                x = 1.0f;
            else if (x < -1.0)
                x = -1.0f;
            temp[i] = (short)  (x * 32767.0);
        }
        doTXAudioRunnable.audioData=temp;
        doTXThreadPool.execute(doTXAudioRunnable);
    }
    private static class DoTXAudioRunnable implements Runnable{
        IcomAudioUdp icomAudioUdp;
        short[] audioData;//传递过来的音频是LPCM 16bit Int,12000hz

        public DoTXAudioRunnable(IcomAudioUdp icomAudioUdp) {
            this.icomAudioUdp = icomAudioUdp;
        }

        @Override
        public void run() {
            if (audioData==null) return;

            final int partialLen = IComPacketTypes.TX_BUFFER_SIZE * 2;//数据包的长度
            //要转换一下到BYTE,小端模式

            //先播放，是给出空的声音，for i 循环，做了一个判断，是给前面的空声音，for j循环，做得判断，是让后面发送空声音
            byte[] audioPacket = new byte[partialLen];
            //用绝对时间表安排20ms的发包节拍，睡眠等待代替忙等。
            //忙等会让一个CPU核在整个发射期间（约13秒）满载空转，整机发热导致降频，影响解码和WiFi。
            long nextSendTime = System.currentTimeMillis();
            for (int i = 0; i < (audioData.length / IComPacketTypes.TX_BUFFER_SIZE) + 8; i++) {//多出6个周期，前面3个，后面3个多
                if (!icomAudioUdp.isPttOn) break;

                icomAudioUdp.sendTrackedPacket(IComPacketTypes.AudioPacket.getTxAudioPacket(audioPacket
                        , (short) 0, icomAudioUdp.localId, icomAudioUdp.remoteId, icomAudioUdp.innerSeq));
                icomAudioUdp.innerSeq++;

                Arrays.fill(audioPacket,(byte)0x00);
                if (i>=3) {//让前两个空数据发送出去
                    for (int j = 0; j < IComPacketTypes.TX_BUFFER_SIZE; j++) {
                        if ((i-3) * IComPacketTypes.TX_BUFFER_SIZE + j < audioData.length) {
                            System.arraycopy(IComPacketTypes.shortToBigEndian((short)
                                            (audioData[(i-3) * IComPacketTypes.TX_BUFFER_SIZE + j]
                                                    * GeneralVariables.volumePercent))//乘以信号量的比率
                                    , 0, audioPacket, j * 2, 2);
                        }
                    }
                }
                nextSendTime += 20;//TX_BUFFER_SIZE=240采样@12kHz，正好20毫秒一包
                long sleepMs = nextSendTime - System.currentTimeMillis();
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            Log.d(TAG, "run: 音频发送完毕！！" );
        }

    }


    @Override
    public void onDataReceived(DatagramPacket packet, byte[] data) {
        super.onDataReceived(packet, data);
        //接收到的是12000采样率的数据
        if (!IComPacketTypes.AudioPacket.isAudioPacket(data)) return;
        byte[] audioData = IComPacketTypes.AudioPacket.getAudioData(data);
        if (onStreamEvents != null) {
            onStreamEvents.OnReceivedAudioData(audioData);
        }
    }
}
