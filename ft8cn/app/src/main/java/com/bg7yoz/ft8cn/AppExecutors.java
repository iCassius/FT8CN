package com.bg7yoz.ft8cn;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 全局线程池管理器，用于统一管理磁盘IO、计算密集型任务和UI调度。
 */
public class AppExecutors {
    private static final Object LOCK = new Object();
    private static volatile AppExecutors sInstance;
    
    private final ExecutorService diskIO;
    private final ExecutorService networkIO;
    private final ExecutorService decoding;
    private final ExecutorService timerTrigger;
    private final ScheduledExecutorService scheduledExecutor;
    private final Executor mainThread;

    private AppExecutors(ExecutorService diskIO, ExecutorService networkIO, 
                        ExecutorService decoding, ExecutorService timerTrigger,
                        ScheduledExecutorService scheduledExecutor,
                        Executor mainThread) {
        this.diskIO = diskIO;
        this.networkIO = networkIO;
        this.decoding = decoding;
        this.timerTrigger = timerTrigger;
        this.scheduledExecutor = scheduledExecutor;
        this.mainThread = mainThread;
    }

    public static AppExecutors getInstance() {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {
                    sInstance = new AppExecutors(
                            Executors.newSingleThreadExecutor(),
                            Executors.newFixedThreadPool(3),
                            Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())),
                            Executors.newSingleThreadExecutor(),
                            Executors.newScheduledThreadPool(2),
                            new MainThreadExecutor()
                    );
                }
            }
        }
        return sInstance;
    }

    public ExecutorService diskIO() {
        return diskIO;
    }

    public ExecutorService networkIO() {
        return networkIO;
    }

    public ExecutorService decoding() {
        return decoding;
    }

    public ExecutorService timerTrigger() {
        return timerTrigger;
    }
    
    public ScheduledExecutorService scheduled() {
        return scheduledExecutor;
    }

    public Executor mainThread() {
        return mainThread;
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            mainThreadHandler.post(command);
        }
    }
    
    public void shutdown() {
        diskIO.shutdown();
        networkIO.shutdown();
        decoding.shutdown();
        timerTrigger.shutdown();
        scheduledExecutor.shutdown();
    }
}
