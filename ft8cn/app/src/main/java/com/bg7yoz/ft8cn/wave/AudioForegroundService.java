package com.bg7yoz.ft8cn.wave;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bg7yoz.ft8cn.R;

public class AudioForegroundService extends Service {
    private static final String TAG = "AudioForegroundService";
    private static final String CHANNEL_ID = "FT8CN_Audio_Channel";
    private static final int NOTIFICATION_ID = 101;
    private static final String ACTION_START = "com.bg7yoz.ft8cn.wave.AudioForegroundService.START";
    private static final String ACTION_STOP = "com.bg7yoz.ft8cn.wave.AudioForegroundService.STOP";
    private static final String EXTRA_SESSION_ID = "session_id";
    private static final long NO_SESSION = 0L;
    private long activeSessionId = NO_SESSION;

    public static Intent createStartIntent(android.content.Context context, long sessionId) {
        return new Intent(context, AudioForegroundService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_ID, sessionId);
    }

    public static Intent createStopIntent(android.content.Context context, long sessionId) {
        return new Intent(context, AudioForegroundService.class)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_SESSION_ID, sessionId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long sessionId = intent == null ? NO_SESSION
                : intent.getLongExtra(EXTRA_SESSION_ID, NO_SESSION);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            if (activeSessionId == sessionId) {
                activeSessionId = NO_SESSION;
                stopForeground(true);
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        activeSessionId = sessionId;
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.decoding))
                .setSmallIcon(R.drawable.ft8cn_icon)
                .build();

        //未授权录音或 app 在后台时，startForeground(microphone) 会抛 SecurityException/
        //ForegroundServiceStartNotAllowedException，必须兜底，否则整个 app 闪退
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.w(TAG, "startForeground failed: " + e.getMessage());
            if (activeSessionId == sessionId) {
                activeSessionId = NO_SESSION;
            }
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "FT8CN Audio Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
