package com.bg7yoz.ft8cn.wave;

import static org.junit.Assert.assertFalse;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bg7yoz.ft8cn.GeneralVariables;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.app.UiAutomation;
import androidx.test.platform.app.InstrumentationRegistry;

@RunWith(AndroidJUnit4.class)
public class RecorderLifecycleTest {
    @Test
    public void microphonePermissionFailureDoesNotReportRecorderRunning() {
        Context context = ApplicationProvider.getApplicationContext();
        GeneralVariables.getInstance().setMainContext(context);
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        boolean wasGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        try {
            automation.revokeRuntimePermission(context.getPackageName(), Manifest.permission.RECORD_AUDIO);

            HamRecorder recorder = new HamRecorder(null);
            recorder.startRecord();

            assertFalse("failed MicRecorder startup must not report HamRecorder running",
                    recorder.isRunning());
            recorder.stopRecord();
        } finally {
            if (wasGranted) {
                automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.RECORD_AUDIO);
            }
        }
    }
}
