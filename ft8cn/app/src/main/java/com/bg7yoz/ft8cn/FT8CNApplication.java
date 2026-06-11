package com.bg7yoz.ft8cn;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * 应用入口。目前只负责注册全局崩溃捕获。
 * 崩溃堆栈写入 Android/data/com.bg7yoz.ft8cn/files/crash/ 目录，
 * 用户可通过电脑USB（MTP）或adb取出文件反馈，不联网、不自动上传。
 */
public class FT8CNApplication extends Application {
    private static final String TAG = "FT8CNApplication";
    private static final int MAX_CRASH_FILES = 10;//最多保留的崩溃文件数

    @Override
    public void onCreate() {
        super.onCreate();
        installCrashHandler();
    }

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previousHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                try {
                    writeCrashFile(thread, throwable);
                } catch (Throwable t) {
                    Log.e(TAG, "写崩溃日志失败: " + t.getMessage());
                }
                //交还给系统默认处理（弹崩溃框/结束进程），不吞掉崩溃
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable);
                }
            }
        });
    }

    private void writeCrashFile(Thread thread, Throwable throwable) throws Exception {
        File dir = getExternalFilesDir("crash");
        if (dir == null) {
            dir = new File(getFilesDir(), "crash");
        }
        if (!dir.exists() && !dir.mkdirs()) return;

        trimOldCrashFiles(dir);

        String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        File crashFile = new File(dir, "crash_" + time + ".txt");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();

        String versionName = "";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }

        try (FileWriter writer = new FileWriter(crashFile)) {
            writer.write("FT8CN版本: " + versionName + "\n");
            writer.write("时间: " + time + "\n");
            writer.write("机型: " + Build.MANUFACTURER + " " + Build.MODEL + "\n");
            writer.write("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n");
            writer.write("线程: " + thread.getName() + "\n");
            writer.write("\n");
            writer.write(sw.toString());
        }
        Log.e(TAG, "崩溃日志已写入: " + crashFile.getAbsolutePath());
    }

    private void trimOldCrashFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length < MAX_CRASH_FILES) return;
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (int i = 0; i <= files.length - MAX_CRASH_FILES; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }
}
