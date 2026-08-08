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
    static final String ACTION_STOP = "com.bg7yoz.ft8cn.wave.AudioForegroundService.STOP";
    private static final String EXTRA_SESSION_ID = "session_id";
    static final String EXTRA_ACK = "session_ack";
    static final String EXTRA_ACK_SESSION_ID = "ack_session_id";
    static final String EXTRA_ACK_START_ID = "ack_start_id";
    static final String EXTRA_ACK_STOP_SELF_START_ID = "ack_stop_self_start_id";
    static final String EXTRA_ACK_STOP_SELF_RESULT = "ack_stop_self_result";
    /** Test-only command hook; it travels with the real service Intent. */
    static final String EXTRA_TEST_FAIL_START_FOREGROUND = "test_fail_start_foreground";
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
        CommandAudit audit = new CommandAudit();
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            int result = sessionLifecycle.stop(sessionId, startId, new SessionLifecycle.Actions() {
                @Override
                public void stopForeground() {
                    AudioForegroundService.this.stopForeground(true);
                }

                @Override
                public boolean stopSelfResult(int commandStartId) {
                    boolean stopped = AudioForegroundService.this.stopSelfResult(commandStartId);
                    audit.stopSelfStartId = commandStartId;
                    audit.stopSelfResult = stopped;
                    return stopped;
                }
            });
            sendAck(intent, result, sessionId, startId, audit);
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.decoding))
                .setSmallIcon(R.drawable.ft8cn_icon)
                .build();
        NotificationStarter notificationStarter = notificationStarterFor(intent);

        int result = sessionLifecycle.start(sessionId, startId, new SessionLifecycle.Actions() {
            @Override
            public void startForeground() throws Exception {
                //未授权录音或 app 在后台时，startForeground(microphone) 会抛
                //SecurityException/ForegroundServiceStartNotAllowedException，交给
                //状态机统一转为 session failure 并终结服务。
                notificationStarter.start(AudioForegroundService.this, notification);
            }

            @Override
            public void stopForeground() {
                AudioForegroundService.this.stopForeground(true);
            }

            @Override
            public boolean stopSelfResult(int commandStartId) {
                boolean stopped = AudioForegroundService.this.stopSelfResult(commandStartId);
                audit.stopSelfStartId = commandStartId;
                audit.stopSelfResult = stopped;
                return stopped;
            }
        });
        sendAck(intent, result, sessionId, startId, audit);

        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void sendAck(Intent intent, int result, long sessionId, int startId,
                         CommandAudit audit) {
        if (intent == null) return;
        ResultReceiver receiver = intent.getParcelableExtra(EXTRA_ACK);
        if (receiver == null) return;
        Bundle data = new Bundle();
        data.putLong(EXTRA_ACK_SESSION_ID, sessionId);
        data.putInt(EXTRA_ACK_START_ID, startId);
        data.putInt(EXTRA_ACK_STOP_SELF_START_ID, audit.stopSelfStartId);
        data.putBoolean(EXTRA_ACK_STOP_SELF_RESULT, audit.stopSelfResult);
        receiver.send(result, data);
    }

    private NotificationStarter notificationStarterFor(Intent intent) {
        NotificationStarter realStarter = (service, notification) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                service.startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                service.startForeground(NOTIFICATION_ID, notification);
            }
        };
        if (intent != null && intent.getBooleanExtra(EXTRA_TEST_FAIL_START_FOREGROUND, false)) {
            return (service, notification) -> {
                // The OS watchdog requires a real foreground promotion before
                // a synthetic post-promotion failure can be observed through
                // the ResultReceiver without killing the target process.
                realStarter.start(service, notification);
                throw new IllegalStateException("injected startForeground failure");
            };
        }
        return realStarter;
    }

    interface NotificationStarter {
        void start(AudioForegroundService service, Notification notification) throws Exception;
    }

    private static final class CommandAudit {
        int stopSelfStartId = -1;
        boolean stopSelfResult;
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
