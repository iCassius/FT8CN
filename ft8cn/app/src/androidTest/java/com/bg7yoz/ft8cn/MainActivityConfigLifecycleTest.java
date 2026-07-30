package com.bg7yoz.ft8cn;

import android.Manifest;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Rule;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Ensures a recreated Activity consumes the shared completion state itself. */
@RunWith(AndroidJUnit4.class)
public class MainActivityConfigLifecycleTest {
    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.POST_NOTIFICATIONS);

    @Test
    public void recreatedActivityConsumesCompletedConfig() throws Exception {
        AtomicReference<MainActivity> first = new AtomicReference<>();
        AtomicReference<MainActivity> second = new AtomicReference<>();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                first.set(activity);
                assertTrue(isConfigUiInitialized(activity));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                second.set(activity);
                assertTrue(isConfigUiInitialized(activity));
            });
        }

        assertNotSame("recreate must create a new Activity instance", first.get(), second.get());
    }

    private static boolean isConfigUiInitialized(MainActivity activity) {
        try {
            Field field = MainActivity.class.getDeclaredField("configUiInitialized");
            field.setAccessible(true);
            return field.getBoolean(activity);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("config UI state is not inspectable", e);
        }
    }
}
