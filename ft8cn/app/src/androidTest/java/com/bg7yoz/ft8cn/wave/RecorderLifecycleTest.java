package com.bg7yoz.ft8cn.wave;

import static org.junit.Assert.assertFalse;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bg7yoz.ft8cn.GeneralVariables;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RecorderLifecycleTest {
    @Test
    public void microphonePermissionFailureDoesNotReportRecorderRunning() {
        Context context = ApplicationProvider.getApplicationContext();
        Context deniedPermissionContext = new ContextWrapper(context) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public int checkSelfPermission(String permission) {
                if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
                    return PackageManager.PERMISSION_DENIED;
                }
                return super.checkSelfPermission(permission);
            }

            @Override
            public int checkPermission(String permission, int pid, int uid) {
                if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
                    return PackageManager.PERMISSION_DENIED;
                }
                return super.checkPermission(permission, pid, uid);
            }
        };
        GeneralVariables.getInstance().setMainContext(deniedPermissionContext);
        HamRecorder recorder = new HamRecorder(null);
        try {
            recorder.startRecord();

            assertFalse("failed MicRecorder startup must not report HamRecorder running",
                    recorder.isRunning());
        } finally {
            recorder.stopRecord();
            GeneralVariables.getInstance().setMainContext(context);
        }
    }
}
