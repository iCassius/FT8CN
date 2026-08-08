package com.bg7yoz.ft8cn.wave;

import android.app.Notification;

/** Test-only Service component that fails at the production promotion boundary. */
public final class FailingAudioForegroundService extends AudioForegroundService {
    @Override
    protected void promoteToForeground(Notification notification) {
        throw new IllegalStateException("test foreground promotion failure");
    }
}
