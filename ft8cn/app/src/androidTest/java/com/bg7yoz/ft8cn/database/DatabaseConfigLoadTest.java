package com.bg7yoz.ft8cn.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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

    private static void insert(DatabaseOpr database, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("KeyName", key);
        values.put("Value", value);
        database.getDb().insertWithOnConflict("config", null, values, 5);
    }
}
