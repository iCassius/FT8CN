package com.bg7yoz.ft8cn.wave;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bg7yoz.ft8cn.R;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AudioForegroundService extends Service {
    private static final String TAG = "AudioForegroundService";
    private static final String CHANNEL_ID = "FT8CN_Audio_Channel";
    private static final int NOTIFICATION_ID = 101;
    private static final String ACTION_START = "com.bg7yoz.ft8cn.wave.AudioForegroundService.START";
    private static final String ACTION_STOP = "com.bg7yoz.ft8cn.wave.AudioForegroundService.STOP";
    private static final String EXTRA_SESSION_ID = "session_id";
    private static final String EXTRA_ACK = "session_ack";
    private static final String EXTRA_ACK_SESSION_ID = "ack_session_id";
    static final int ACK_STARTED = 1;
    static final int ACK_FAILED = 2;
    static final int ACK_STOPPED = 3;
    static final int ACK_STALE = 4;
    private static final long NO_SESSION = 0L;
    private static final long START_ACK_TIMEOUT_MILLIS = 3000L;
    private final SessionLifecycle sessionLifecycle = new SessionLifecycle();

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
            int result = sessionLifecycle.stop(sessionId, startId, new SessionLifecycle.Actions() {
                @Override
                public void stopForeground() {
                    AudioForegroundService.this.stopForeground(true);
                }

                @Override
                public boolean stopSelfResult(int commandStartId) {
                    return AudioForegroundService.this.stopSelfResult(commandStartId);
                }
            });
            sendAck(intent, result, sessionId);
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.decoding))
                .setSmallIcon(R.drawable.ft8cn_icon)
                .build();

        int result = sessionLifecycle.start(sessionId, startId, new SessionLifecycle.Actions() {
            @Override
            public void startForeground() throws Exception {
                //未授权录音或 app 在后台时，startForeground(microphone) 会抛
                //SecurityException/ForegroundServiceStartNotAllowedException，交给
                //状态机统一转为 session failure 并终结服务。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    AudioForegroundService.this.startForeground(NOTIFICATION_ID, notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                } else {
                    AudioForegroundService.this.startForeground(NOTIFICATION_ID, notification);
                }
            }

            @Override
            public void stopForeground() {
                AudioForegroundService.this.stopForeground(true);
            }

            @Override
            public boolean stopSelfResult(int commandStartId) {
                return AudioForegroundService.this.stopSelfResult(commandStartId);
            }
        });
        sendAck(intent, result, sessionId);

        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void sendAck(Intent intent, int result, long sessionId) {
        if (intent == null) return;
        ResultReceiver receiver = intent.getParcelableExtra(EXTRA_ACK);
        if (receiver == null) return;
        Bundle data = new Bundle();
        data.putLong(EXTRA_ACK_SESSION_ID, sessionId);
        receiver.send(result, data);
    }

    static final class SessionLifecycle {
        interface Actions {
            default void startForeground() throws Exception {
            }

            void stopForeground();

            boolean stopSelfResult(int startId);
        }

        private long activeSessionId = NO_SESSION;
        private int activeStartId;

        int start(long sessionId, int startId, Actions actions) {
            if (sessionId == NO_SESSION) {
                actions.stopForeground();
                actions.stopSelfResult(startId);
                return ACK_FAILED;
            }
            if (activeSessionId != NO_SESSION && sessionId != activeSessionId
                    && startId <= activeStartId) {
                return ACK_STALE;
            }

            activeSessionId = sessionId;
            activeStartId = startId;
            try {
                actions.startForeground();
                return ACK_STARTED;
            } catch (Throwable failure) {
                Log.w(TAG, "startForeground failed: " + failure.getMessage());
                if (activeSessionId == sessionId && activeStartId == startId) {
                    activeSessionId = NO_SESSION;
                    actions.stopForeground();
                    actions.stopSelfResult(startId);
                }
                return ACK_FAILED;
            }
        }

        int stop(long sessionId, int startId, Actions actions) {
            if (activeSessionId == NO_SESSION) {
                actions.stopForeground();
                actions.stopSelfResult(startId);
                return ACK_STOPPED;
            }
            if (activeSessionId != sessionId) {
                // A stale STOP must not call stopSelfResult: its Android startId
                // may be newer than the START that owns the active session.
                return ACK_STALE;
            }
            activeSessionId = NO_SESSION;
            actions.stopForeground();
            actions.stopSelfResult(startId);
            return ACK_STOPPED;
        }

        long activeSessionId() {
            return activeSessionId;
        }
    }

    static final class StartAck extends ResultReceiver {
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile int result = ACK_FAILED;

        StartAck() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            result = resultCode;
            completed.countDown();
        }

        boolean await() {
            try {
                return completed.await(START_ACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                        && result == ACK_STARTED;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
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
