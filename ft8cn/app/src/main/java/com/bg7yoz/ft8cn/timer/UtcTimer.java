package com.bg7yoz.ft8cn.timer;

import android.annotation.SuppressLint;
import android.util.Log;

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
    
    private final ExecutorService actionThreadPool = AppExecutors.getInstance().timerTrigger();
    private final Executor heartBeatThreadPool = AppExecutors.getInstance().mainThread();
    private final HeartbeatLifecycleGate heartbeatLifecycle = new HeartbeatLifecycleGate();
    private final long heartbeatEpoch = heartbeatLifecycle.currentEpoch();
    
    private final Runnable doHeartBeat = new Runnable() {
        @Override
        public void run() {
            heartbeatLifecycle.runIfCurrent(heartbeatEpoch,
                    () -> onUtcTimer.doHeartBeatTimer(utc));
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
        // time_sec 是 100ms 为单位的偏移量，需要转换为毫秒
        long timeInSlot = (now - (time_sec * 100L)) % slotMs;
        if (timeInSlot < 0) timeInSlot += slotMs; // 确保正数
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
                    try {
                        actionThreadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                if (onUtcTimer != null) {
                                    onUtcTimer.doOnSecTimer(triggerUtc);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e("UtcTimer", "Error executing timer action: " + e.getMessage());
                    }

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
        heartbeatLifecycle.close();
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
        AppExecutors.getInstance().networkIO().execute(() -> {
            // 优先使用中国境内高精度 NTP 服务器
            String[] ntpServers = {
                    "ntp.aliyun.com",
                    "ntp1.aliyun.com",
                    "ntp.tencent.com",
                    "time.edu.cn",
                    "cn.pool.ntp.org",
                    "time.apple.com",
                    "time.windows.com"
            };

            NTPUDPClient timeClient = new NTPUDPClient();
            timeClient.setDefaultTimeout(3000); // 3秒超时

            for (String server : ntpServers) {
                try {
                    InetAddress inetAddress = InetAddress.getByName(server);
                    TimeInfo timeInfo = timeClient.getTime(inetAddress);
                    timeInfo.computeDetails();
                    Long offset = timeInfo.getOffset();

                    if (offset != null) {
                        // 更新全局偏移量
                        UtcTimer.delay = offset.intValue();
                        android.util.Log.i("UtcTimer", String.format("NTP同步成功! 服务器: %s, 偏移量: %dms", server, UtcTimer.delay));

                        if (afterSyncTime != null) {
                            AppExecutors.getInstance().mainThread().execute(() ->
                                    afterSyncTime.doAfterSyncTimer(UtcTimer.delay));
                        }
                        return; // 同步成功，退出循环
                    }
                } catch (Exception e) {
                    // 当前服务器失败，尝试下一个
                    android.util.Log.w("UtcTimer", "NTP server " + server + " failed: " + e.getMessage());
                }
            }

            // 全部失败
            if (afterSyncTime != null) {
                AppExecutors.getInstance().mainThread().execute(() ->
                        afterSyncTime.syncFailed(new IOException("所有 NTP 服务器均连接失败，请检查网络")));
            }
        });
    }

    public interface AfterSyncTime {
        void doAfterSyncTimer(int secTime);
        void syncFailed(IOException e);
    }
}
