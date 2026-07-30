package com.bg7yoz.ft8cn.database;

import android.Manifest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.core.content.ContextCompat;

import com.bg7yoz.ft8cn.GeneralVariables;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Verifies that row callbacks and the single load-complete callback stay distinct. */
@RunWith(AndroidJUnit4.class)
public class DatabaseConfigLoadTest {
    @Test
    public void completeFiresOnceAfterAllRowsAreVisible() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseOpr database = new DatabaseOpr(context, "config_load_callback_test", null, 15);
        database.getDb().delete("config", null, null);
        insert(database, "callsign", "BG7YOZ");
        insert(database, "grid", "OM88");

        AtomicInteger rowCallbacks = new AtomicInteger();
        AtomicInteger completeCallbacks = new AtomicInteger();
        CountDownLatch complete = new CountDownLatch(1);
        database.getAllConfigParameter(new OnAfterQueryConfig() {
            @Override
            public void doOnBeforeQueryConfig(String keyName) {
            }

            @Override
            public void doOnAfterQueryConfig(String keyName, String value) {
                assertFalse("a row callback must not be the completion event", completeCallbacks.get() > 0);
                rowCallbacks.incrementAndGet();
            }

            @Override
            public void doOnConfigLoadComplete() {
                assertEquals(1, completeCallbacks.incrementAndGet());
                assertEquals("BG7YOZ", GeneralVariables.myCallsign);
                assertEquals("OM88", GeneralVariables.getMyMaidenheadGrid());
                complete.countDown();
            }
        });

        assertTrue("config load did not complete", complete.await(10, TimeUnit.SECONDS));
        assertEquals(2, rowCallbacks.get());
        assertEquals(1, completeCallbacks.get());
    }

    @Test
    public void emptyConfigStillCompletesOnce() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseOpr database = new DatabaseOpr(context, "config_load_empty_test", null, 15);
        database.getDb().delete("config", null, null);

        AtomicInteger completeCallbacks = new AtomicInteger();
        CountDownLatch complete = new CountDownLatch(1);
        database.getAllConfigParameter(new OnAfterQueryConfig() {
            @Override
            public void doOnBeforeQueryConfig(String keyName) {
            }

            @Override
            public void doOnAfterQueryConfig(String keyName, String value) {
                throw new AssertionError("empty config has no row callback");
            }

            @Override
            public void doOnConfigLoadComplete() {
                completeCallbacks.incrementAndGet();
                complete.countDown();
            }
        });

        assertTrue("empty config load did not complete", complete.await(10, TimeUnit.SECONDS));
        assertEquals(1, completeCallbacks.get());
    }

    @Test
    public void damagedNumericConfigUsesDefaultsAndStillCompletesOnce() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseOpr database = new DatabaseOpr(context, "config_load_bad_numeric_test", null, 15);
        database.getDb().delete("config", null, null);
        insert(database, "transDelay", "not-a-number");
        insert(database, "audioRate", "broken");
        insert(database, "freq", "NaN");

        AtomicInteger completeCallbacks = new AtomicInteger();
        CountDownLatch complete = new CountDownLatch(1);
        database.getAllConfigParameter(new OnAfterQueryConfig() {
            @Override
            public void doOnBeforeQueryConfig(String keyName) {
            }

            @Override
            public void doOnAfterQueryConfig(String keyName, String value) {
            }

            @Override
            public void doOnConfigLoadComplete() {
                completeCallbacks.incrementAndGet();
                assertEquals(500, GeneralVariables.transmitDelay);
                assertEquals(12000, GeneralVariables.audioSampleRate);
                assertEquals(1000f, GeneralVariables.getBaseFrequency(), 0.001f);
                complete.countDown();
            }
        });

        assertTrue("damaged config load did not complete", complete.await(10, TimeUnit.SECONDS));
        assertEquals(1, completeCallbacks.get());
    }

    @Test
    public void configLoadCompletesWithoutRuntimePermission() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Context noAudioPermissionContext = new ContextWrapper(context) {
            @Override
            public int checkPermission(String permission, int pid, int uid) {
                if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
                    return PackageManager.PERMISSION_DENIED;
                }
                return super.checkPermission(permission, pid, uid);
            }
        };
        assertEquals(PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(noAudioPermissionContext, Manifest.permission.RECORD_AUDIO));

        DatabaseOpr database = new DatabaseOpr(noAudioPermissionContext,
                "config_load_without_permission_test", null, 15);
        database.getDb().delete("config", null, null);
        CountDownLatch complete = new CountDownLatch(1);
        database.getAllConfigParameter(new OnAfterQueryConfig() {
            @Override
            public void doOnBeforeQueryConfig(String keyName) {
            }

            @Override
            public void doOnAfterQueryConfig(String keyName, String value) {
            }

            @Override
            public void doOnConfigLoadComplete() {
                complete.countDown();
            }
        });
        assertTrue("config completion was lost without runtime permission",
                complete.await(10, TimeUnit.SECONDS));
    }

    private static void insert(DatabaseOpr database, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("KeyName", key);
        values.put("Value", value);
        database.getDb().insertWithOnConflict("config", null, values, 5);
    }
}
