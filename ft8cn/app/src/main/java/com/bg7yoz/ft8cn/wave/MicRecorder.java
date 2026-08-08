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

import java.util.concurrent.atomic.AtomicBoolean;

interface AudioRecordFactory {
    int getMinBufferSize();

    AudioRecordHandle create(int bufferSize);
}

interface AudioRecordHandle {
    int getState();

    int getRecordingState();

    void startRecording();

    int read(short[] buffer, int offset, int size);

    void stop();

    void release();
}

interface ForegroundServiceController {
    boolean start(long sessionId);

    void stop(long sessionId);
}

public class MicRecorder {
    private static final String TAG = "MicRecorder";
    private static final int SAMPLE_RATE_IN_HZ = 12000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final Object lifecycleLock = new Object();
    private final AudioRecordFactory audioRecordFactory;
    private final ForegroundServiceController foregroundServiceController;
    private long nextSessionId;
    private Session currentSession;
    private volatile boolean isRunning;
    private volatile OnDataListener onDataListener;
    private volatile OnStateListener onStateListener;
    private volatile float[] reusableFloatBuffer;

    public interface OnDataListener {
        void onDataReceived(float[] data, int len);
    }

    interface OnStateListener {
        void onStateChanged(long sessionId, boolean running);
    }

    private static final class Session {
        final long id;
        final AtomicBoolean cleaned = new AtomicBoolean();
        volatile boolean cancelled;
        volatile boolean serviceStarted;
        volatile AudioRecordHandle audioRecord;
        volatile Thread worker;
        int bufferSize;

        Session(long id) {
            this.id = id;
        }
    }

    public MicRecorder() {
        this(new AndroidAudioRecordFactory(),
                new AndroidForegroundServiceController(GeneralVariables.getMainContext()));
    }

    MicRecorder(AudioRecordFactory audioRecordFactory,
                ForegroundServiceController foregroundServiceController) {
        this.audioRecordFactory = audioRecordFactory;
        this.foregroundServiceController = foregroundServiceController;
    }

    /**
     * Keep the historical boolean API for callers that only need to know that
     * a start request was accepted. The actual device initialization and the
     * running truth are completed asynchronously on the session worker.
     */
    public boolean start() {
        return startSession() != 0;
    }

    /** Returns the session identity for a start request, or zero on rejection. */
    long startSession() {
        synchronized (lifecycleLock) {
            if (currentSession != null) {
                return currentSession.id;
            }

            Session session = new Session(++nextSessionId);
            currentSession = session;
            Thread worker = new Thread(() -> runSession(session), "FT8CN-MicRecorder");
            session.worker = worker;
            worker.start();
            return session.id;
        }
    }

    private void runSession(Session session) {
        boolean running = false;
        try {
            if (!isCurrent(session)) {
                return;
            }

            if (!foregroundServiceController.start(session.id)) {
                return;
            }
            boolean staleAfterServiceStart;
            synchronized (lifecycleLock) {
                staleAfterServiceStart = currentSession != session || session.cancelled;
                if (!staleAfterServiceStart) {
                    session.serviceStarted = true;
                }
            }
            if (staleAfterServiceStart) {
                try {
                    foregroundServiceController.stop(session.id);
                } catch (Throwable error) {
                    Log.w(TAG, "stopAudioForegroundService: " + error.getMessage());
                }
                return;
            }

            if (!attachAudioRecord(session) || !startRecordingWithRetry(session)) {
                return;
            }

            if (!markRunning(session)) {
                return;
            }
            running = true;
            readLoop(session);
        } catch (Throwable error) {
            Log.w(TAG, "recording session failed: " + error.getMessage());
        } finally {
            if (!running && !session.cancelled) {
                try {
                    ToastMessage.show(String.format(GeneralVariables.getStringFromResource(
                            R.string.recorder_cannot_record), "AudioRecord is not initialized"));
                } catch (Throwable error) {
                    Log.w(TAG, "recording failure notification failed: " + error.getMessage());
                }
            }
            cleanupSession(session);
        }
    }

    private boolean attachAudioRecord(Session session) {
        int minBufferSize;
        try {
            minBufferSize = audioRecordFactory.getMinBufferSize();
        } catch (Throwable error) {
            Log.w(TAG, "getMinBufferSize failed: " + error.getMessage());
            return false;
        }
        session.bufferSize = minBufferSize > 0 ? minBufferSize : SAMPLE_RATE_IN_HZ;

        AudioRecordHandle record;
        try {
            record = audioRecordFactory.create(session.bufferSize);
        } catch (Throwable error) {
            Log.w(TAG, "AudioRecord initialization failed: " + error.getMessage());
            return false;
        }
        boolean initialized;
        try {
            initialized = record != null && record.getState() == AudioRecord.STATE_INITIALIZED;
        } catch (Throwable error) {
            initialized = false;
            Log.w(TAG, "AudioRecord state check failed: " + error.getMessage());
        }
        if (!initialized) {
            safeRelease(record);
            Log.w(TAG, "AudioRecord is not initialized");
            return false;
        }

        boolean stale;
        synchronized (lifecycleLock) {
            stale = currentSession != session || session.cancelled;
            if (!stale) {
                session.audioRecord = record;
            }
        }
        if (stale) {
            safeRelease(record);
            return false;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private boolean startRecordingWithRetry(Session session) {
        if (startRecordingOnce(session)) {
            return true;
        }
        detachAndRelease(session);
        if (!isCurrent(session) || !attachAudioRecord(session)) {
            return false;
        }
        return startRecordingOnce(session);
    }

    @SuppressLint("MissingPermission")
    private boolean startRecordingOnce(Session session) {
        AudioRecordHandle record = session.audioRecord;
        if (record == null || !isCurrent(session)) {
            return false;
        }
        try {
            record.startRecording();
            if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                return true;
            }
        } catch (Throwable error) {
            Log.w(TAG, "startRecording failed: " + error.getMessage());
        }
        return false;
    }

    private boolean markRunning(Session session) {
        synchronized (lifecycleLock) {
            if (currentSession != session || session.cancelled) {
                return false;
            }
            isRunning = true;
        }
        notifyState(session.id, true);
        return isCurrent(session);
    }

    private void readLoop(Session session) {
        short[] buffer = new short[Math.max(1, session.bufferSize / 2)];
        while (isCurrent(session) && isRunning) {
            AudioRecordHandle record = session.audioRecord;
            if (record == null || record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "record failed: AudioRecord is no longer recording");
                return;
            }

            int bufferReadResult = record.read(buffer, 0, buffer.length);
            if (bufferReadResult <= 0) {
                Log.w(TAG, "record loop read error: " + bufferReadResult);
                if (bufferReadResult == AudioRecord.ERROR_DEAD_OBJECT
                        || bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION
                        || bufferReadResult == AudioRecord.ERROR_BAD_VALUE) {
                    return;
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

            OnDataListener listener = onDataListener;
            if (listener != null && isCurrent(session)) {
                listener.onDataReceived(reusableFloatBuffer, bufferReadResult);
            }
        }
    }

    private boolean isCurrent(Session session) {
        synchronized (lifecycleLock) {
            return currentSession == session && !session.cancelled;
        }
    }

    private void detachAndRelease(Session session) {
        AudioRecordHandle record;
        synchronized (lifecycleLock) {
            record = session.audioRecord;
            session.audioRecord = null;
        }
        safeRelease(record);
    }

    private void cleanupSession(Session session) {
        if (!session.cleaned.compareAndSet(false, true)) {
            return;
        }

        AudioRecordHandle record;
        boolean notifyStopped;
        synchronized (lifecycleLock) {
            record = session.audioRecord;
            session.audioRecord = null;
            notifyStopped = currentSession == session;
            if (notifyStopped) {
                currentSession = null;
                isRunning = false;
            }
        }

        safeRelease(record);
        if (session.serviceStarted) {
            try {
                foregroundServiceController.stop(session.id);
            } catch (Throwable error) {
                Log.w(TAG, "stopAudioForegroundService: " + error.getMessage());
            }
        }
        if (notifyStopped) {
            notifyState(session.id, false);
        }
    }

    private static void safeRelease(AudioRecordHandle record) {
        if (record == null) {
            return;
        }
        try {
            if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop();
            }
        } catch (Throwable error) {
            Log.w(TAG, "AudioRecord stop failed: " + error.getMessage());
        }
        try {
            record.release();
        } catch (Throwable error) {
            Log.w(TAG, "AudioRecord release failed: " + error.getMessage());
        }
    }

    private void notifyState(long sessionId, boolean running) {
        OnStateListener listener = onStateListener;
        if (listener != null) {
            listener.onStateChanged(sessionId, running);
        }
    }

    public void stopRecord() {
        long sessionId;
        synchronized (lifecycleLock) {
            sessionId = currentSession == null ? 0 : currentSession.id;
        }
        stopSession(sessionId);
    }

    void stopSession(long sessionId) {
        Session session;
        synchronized (lifecycleLock) {
            session = currentSession;
            if (session == null || session.id != sessionId) {
                return;
            }
            session.cancelled = true;
            currentSession = null;
            isRunning = false;
        }
        Thread worker = session.worker;
        if (worker != null) {
            worker.interrupt();
        }
        cleanupSession(session);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public OnDataListener getOnDataListener() {
        return onDataListener;
    }

    public void setOnDataListener(OnDataListener onDataListener) {
        this.onDataListener = onDataListener;
    }

    void setOnStateListener(OnStateListener onStateListener) {
        this.onStateListener = onStateListener;
    }

    private static final class AndroidAudioRecordFactory implements AudioRecordFactory {
        @Override
        public int getMinBufferSize() {
            return AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT);
        }

        @Override
        public AudioRecordHandle create(int bufferSize) {
            return new AndroidAudioRecord(new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize));
        }
    }

    private static final class AndroidAudioRecord implements AudioRecordHandle {
        private final AudioRecord delegate;

        AndroidAudioRecord(AudioRecord delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getState() {
            return delegate.getState();
        }

        @Override
        public int getRecordingState() {
            return delegate.getRecordingState();
        }

        @Override
        public void startRecording() {
            delegate.startRecording();
        }

        @Override
        public int read(short[] buffer, int offset, int size) {
            return delegate.read(buffer, offset, size);
        }

        @Override
        public void stop() {
            delegate.stop();
        }

        @Override
        public void release() {
            delegate.release();
        }
    }

    private static final class AndroidForegroundServiceController implements ForegroundServiceController {
        private final Context context;

        AndroidForegroundServiceController(Context context) {
            this.context = context;
        }

        @Override
        @SuppressLint("MissingPermission")
        public boolean start(long sessionId) {
            if (context == null) {
                return true;
            }
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "start: RECORD_AUDIO not granted, skip recording until permission granted.");
                return false;
            }
            try {
                Intent intent = AudioForegroundService.createStartIntent(context, sessionId);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
                return true;
            } catch (Throwable error) {
                Log.w(TAG, "startAudioForegroundService: " + error.getMessage());
                return false;
            }
        }

        @Override
        public void stop(long sessionId) {
            if (context == null) {
                return;
            }
            try {
                context.startService(AudioForegroundService.createStopIntent(context, sessionId));
            } catch (Throwable error) {
                Log.w(TAG, "stopAudioForegroundService: " + error.getMessage());
            }
        }
    }
}
