package com.bg7yoz.ft8cn.timer;

import android.annotation.SuppressLint;

import com.bg7yoz.ft8cn.AppExecutors;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * UtcTimer类，用于实现FT8在各通联周期开始时触发的动作。
 * 已优化：移除了10ms轮询，改为使用ScheduledExecutorService精确触发。
 */
public class UtcTimer {
    private final int sec;
    private final boolean doOnce;
    private final OnUtcTimer onUtcTimer;

    private long utc;
    public static int delay = 0;//时钟总的延时，（毫秒）
    private boolean running = false;//用来判断是否触发周期的动作

    private final ScheduledExecutorService scheduler = AppExecutors.getInstance().scheduled();
    private ScheduledFuture<?> nextSlotTask;
    private final Timer heartBeatTimer = new Timer();
    private int time_sec = 0;//时间的偏移量；
    
    private final ExecutorService actionThreadPool = AppExecutors.getInstance().decoding();
    private final Executor heartBeatThreadPool = AppExecutors.getInstance().mainThread();
    
    private final Runnable doHeartBeat = new Runnable() {
        @Override
        public void run() {
            onUtcTimer.doHeartBeatTimer(utc);
        }
    };

    @SuppressLint("DefaultLocale")
    public static String getTimeStr(long time) {
        long curtime = time / 1000;
        long hour = ((curtime) / (60 * 60)) % 24;
        long sec = (curtime) % 60;
        long min = ((curtime) % 3600) / 60;
        return String.format("UTC : %02d:%02d:%02d", hour, min, sec);
    }

    @SuppressLint("DefaultLocale")
    public static String getTimeHHMMSS(long time) {
        long curtime = time / 1000;
        long hour = ((curtime) / (60 * 60)) % 24;
        long sec = (curtime) % 60;
        long min = ((curtime) % 3600) / 60;
        return String.format("%02d%02d%02d", hour, min, sec);
    }

    public static String getYYYYMMDD(long time) {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return simpleDateFormat.format(new Date(time));
    }

    public static String getDatetimeStr(long time) {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return simpleDateFormat.format(new Date(time));
    }

    public static String getDatetimeYYYYMMDD_HHMMSS(long time) {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return simpleDateFormat.format(new Date(time));
    }

    public UtcTimer(int sec, boolean doOnce, OnUtcTimer onUtcTimer) {
        this.sec = sec;
        this.doOnce = doOnce;
        this.onUtcTimer = onUtcTimer;
        this.utc = getSystemTime();

        heartBeatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                utc = getSystemTime();
                heartBeatThreadPool.execute(doHeartBeat);
            }
        }, 0, 500); // 提升频率到 500ms，确保 UI 刷新流畅 (每秒至少 2 次)
    }

    private synchronized void scheduleNextSlot() {
        if (!running) return;
        if (nextSlotTask != null && !nextSlotTask.isDone()) {
            nextSlotTask.cancel(false);
        }

        long now = getSystemTime();
        long slotMs = sec * 100L;
        long timeInSlot = (now - time_sec) % slotMs;
        long delayToNext = slotMs - timeInSlot;

        // 避免极短时间内重复触发，且确保对齐到槽位
        if (delayToNext < 20) {
            delayToNext += slotMs;
        }

        nextSlotTask = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if (running) {
                    final long triggerUtc = getSystemTime();
                    actionThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            onUtcTimer.doOnSecTimer(triggerUtc);
                        }
                    });

                    if (doOnce) {
                        running = false;
                    } else {
                        // 修正：不再等待 1 秒，而是直接调度下一个槽位
                        scheduleNextSlot();
                    }
                }
            }
        }, delayToNext, TimeUnit.MILLISECONDS);
    }


    public synchronized void stop() {
        running = false;
        if (nextSlotTask != null) {
            nextSlotTask.cancel(false);
        }
    }

    public synchronized void start() {
        if (!running) {
            running = true;
            scheduleNextSlot();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void delete() {
        stop();
        heartBeatTimer.cancel();
    }

    public void setTime_sec(int time_sec) {
        this.time_sec = time_sec;
        if (running) {
            scheduleNextSlot();
        }
    }

    public int getTime_sec() {
        return time_sec;
    }

    public long getUtc() {
        return utc;
    }

    public static int sequential(long utc) {
        return (int) ((((utc) / 1000) / 15) % 2);
    }

    public static int getNowSequential() {
        return sequential(getSystemTime());
    }

    public static long getSystemTime() {
        return delay + System.currentTimeMillis();
    }

    public static void syncTime(AfterSyncTime afterSyncTime) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                NTPUDPClient timeClient = new NTPUDPClient();
                try {
                    InetAddress inetAddress = InetAddress.getByName("time.windows.com");
                    TimeInfo timeInfo = timeClient.getTime(inetAddress);
                    long serverTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();
                    int trueDelay = (int) ((serverTime - System.currentTimeMillis()));
                    UtcTimer.delay = trueDelay % 15000;
                    if (afterSyncTime != null) {
                        afterSyncTime.doAfterSyncTimer(trueDelay);
                    }
                } catch (IOException e) {
                    if (afterSyncTime != null) {
                        afterSyncTime.syncFailed(e);
                    }
                }
            }
        }).start();
    }

    public interface AfterSyncTime {
        void doAfterSyncTimer(int secTime);
        void syncFailed(IOException e);
    }
}
