package com.bg7yoz.ft8cn;

import android.Manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Rule;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.bg7yoz.ft8cn.MainViewModel;
import com.bg7yoz.ft8cn.database.DatabaseOpr;

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
        assertEquals("original Activity must initialize once", 1,
                getConfigUiCompletionActionCount(first.get()));
        assertEquals("recreated Activity must initialize once", 1,
                getConfigUiCompletionActionCount(second.get()));
    }

    @Test
    public void blockedLoadIsConsumedOnlyByRecreatedActivity() throws Exception {
        AtomicReference<MainActivity> oldActivity = new AtomicReference<>();
        AtomicReference<MainActivity> newActivity = new AtomicReference<>();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch blockerFinished = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        try {
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(oldActivity::set);
                scenario.onActivity(activity -> {
                    assertEquals(1, getConfigUiCompletionActionCount(activity));
                    resetConfigLoadForTest(getMainViewModel(activity));
                    resetConfigUiForTest(activity);
                });

                MainViewModel viewModel = getMainViewModel(oldActivity.get());
                getConfigDatabaseExecutor(viewModel.databaseOpr).submit(() -> {
                    blockerStarted.countDown();
                    try {
                        releaseLoad.await();
                    } finally {
                        blockerFinished.countDown();
                    }
                    return null;
                });
                assertTrue("database blocker did not start",
                        blockerStarted.await(10, TimeUnit.SECONDS));
                scenario.onActivity(activity -> viewModel.loadConfigIfNeeded());
                assertTrue("old Activity did not enter config loading",
                        viewModel.configIsLoading);
                assertFalse("config unexpectedly completed before recreate",
                        blockerFinished.await(100, TimeUnit.MILLISECONDS));
                scenario.recreate();
                scenario.onActivity(activity -> {
                    newActivity.set(activity);
                    assertSame("recreate must retain the shared MainViewModel", viewModel,
                            getMainViewModel(activity));
                    assertEquals(0, getConfigUiCompletionActionCount(activity));
                });

                CountDownLatch complete = new CountDownLatch(1);
                androidx.lifecycle.Observer<Boolean> observer = value -> {
                    if (Boolean.TRUE.equals(value)) complete.countDown();
                };
                scenario.onActivity(activity -> viewModel.configLoadComplete.observeForever(observer));
                try {
                    releaseLoad.countDown();
                    assertTrue("blocked config load did not complete", complete.await(10, TimeUnit.SECONDS));
                } finally {
                    scenario.onActivity(activity -> viewModel.configLoadComplete.removeObserver(observer));
                }

                assertEquals("destroyed Activity must not consume completion", 0,
                        getConfigUiCompletionActionCount(oldActivity.get()));
                assertEquals("recreated Activity must consume completion once", 1,
                        getConfigUiCompletionActionCount(newActivity.get()));
            }
        } finally {
            releaseLoad.countDown();
        }
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

    private static int getConfigUiCompletionActionCount(MainActivity activity) {
        try {
            Field field = MainActivity.class.getDeclaredField("configUiCompletionActionCount");
            field.setAccessible(true);
            return field.getInt(activity);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("config UI action counter is not inspectable", e);
        }
    }

    private static MainViewModel getMainViewModel(MainActivity activity) {
        try {
            Field field = MainActivity.class.getDeclaredField("mainViewModel");
            field.setAccessible(true);
            return (MainViewModel) field.get(activity);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("MainViewModel is not inspectable", e);
        }
    }

    private static void resetConfigLoadForTest(MainViewModel viewModel) {
        try {
            Field field = MainViewModel.class.getDeclaredField("configLoadStarted");
            field.setAccessible(true);
            field.setBoolean(viewModel, false);
            viewModel.configIsLoading = false;
            viewModel.configIsLoaded = false;
            viewModel.configLoadComplete.setValue(false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("config load state is not resettable", e);
        }
    }

    private static void resetConfigUiForTest(MainActivity activity) {
        try {
            Field initialized = MainActivity.class.getDeclaredField("configUiInitialized");
            initialized.setAccessible(true);
            initialized.setBoolean(activity, false);
            Field actionCount = MainActivity.class.getDeclaredField("configUiCompletionActionCount");
            actionCount.setAccessible(true);
            actionCount.setInt(activity, 0);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("config UI state is not resettable", e);
        }
    }

    private static java.util.concurrent.ExecutorService getConfigDatabaseExecutor(DatabaseOpr database) {
        try {
            Field field = database.getClass().getDeclaredField("dbExecutor");
            field.setAccessible(true);
            return (java.util.concurrent.ExecutorService) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("config database executor is not inspectable", e);
        }
    }
}
