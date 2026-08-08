package com.bg7yoz.ft8cn.wave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import androidx.test.core.app.ApplicationProvider;
import androidx.core.content.ContextCompat;
import androidx.test.rule.GrantPermissionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.After;
import org.junit.Rule;
import org.junit.runner.RunWith;

/** Exercises the service command state machine without revoking system permissions. */
@RunWith(AndroidJUnit4.class)
public class AudioForegroundServiceLifecycleTest {
    @Rule
    public final GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.RECORD_AUDIO);

    @After
    public void stopRealServiceAfterTest() {
        context().stopService(new Intent(context(), AudioForegroundService.class));
        context().stopService(new Intent(context(), FailingAudioForegroundService.class));
    }

    @Test
    public void realServiceAcknowledgesStartAndMatchingStop() throws Exception {
        Ack started = sendRealCommand(AudioForegroundService.createStartIntent(context(), 401));
        assertEquals(AudioForegroundService.ACK_STARTED, started.resultCode);
        assertEquals(401L, started.sessionId);
        assertTrue("service did not report a real startId", started.startId > 0);
        assertEquals(-1, started.stopSelfStartId);

        Ack stopped = sendRealCommand(AudioForegroundService.createStopIntent(context(), 401));
        assertEquals(AudioForegroundService.ACK_STOPPED, stopped.resultCode);
        assertEquals(401L, stopped.sessionId);
        assertTrue("matching STOP did not call stopSelfResult",
                stopped.stopSelfStartId > started.startId);
        assertTrue(stopped.stopSelfResult);
    }

    @Test
    public void realServiceKeepsNewSessionAcrossStaleStopAndStopsNoActiveCommand() throws Exception {
        Ack first = sendRealCommand(AudioForegroundService.createStartIntent(context(), 501));
        Ack second = sendRealCommand(AudioForegroundService.createStartIntent(context(), 502));
        assertEquals(AudioForegroundService.ACK_STARTED, first.resultCode);
        assertEquals(AudioForegroundService.ACK_STARTED, second.resultCode);
        assertTrue(second.startId > first.startId);

        Ack stale = sendRealCommand(AudioForegroundService.createStopIntent(context(), 501));
        assertEquals(AudioForegroundService.ACK_STALE, stale.resultCode);
        assertEquals(-1, stale.stopSelfStartId);

        Ack matching = sendRealCommand(AudioForegroundService.createStopIntent(context(), 502));
        assertEquals(AudioForegroundService.ACK_STOPPED, matching.resultCode);
        assertTrue(matching.stopSelfStartId > second.startId);
        assertTrue(matching.stopSelfResult);

        Ack noActive = sendRealCommand(AudioForegroundService.createStopIntent(context(), 599));
        assertEquals(AudioForegroundService.ACK_STOPPED, noActive.resultCode);
        assertEquals("no-active STOP did not use its own restarted service startId",
                noActive.startId, noActive.stopSelfStartId);
        assertTrue(noActive.startId > 0);
        assertTrue(noActive.stopSelfResult);
    }

    @Test
    public void testServicePropagatesPromotionFailureAndCleansUp() throws Exception {
        Intent start = AudioForegroundService.createStartIntent(context(), 601)
                .setComponent(new android.content.ComponentName(
                        context(), FailingAudioForegroundService.class));
        Ack failed = sendRealCommand(start);
        assertEquals(AudioForegroundService.ACK_FAILED, failed.resultCode);
        assertEquals(601L, failed.sessionId);
        assertTrue("failed foreground start did not stop the service command",
                failed.stopSelfStartId > 0);
        assertTrue(failed.stopSelfResult);
    }

    @Test
    public void staleStopDoesNotStopTheNewActiveSessionAndNoActiveStopsReliably() {
        AudioForegroundService.SessionLifecycle lifecycle =
                new AudioForegroundService.SessionLifecycle();
        FakeActions actions = new FakeActions();

        assertEquals(AudioForegroundService.ACK_STARTED,
                lifecycle.start(101, 10, actions));
        assertEquals(AudioForegroundService.ACK_STARTED,
                lifecycle.start(202, 11, actions));
        int eventsBeforeStaleStop = actions.events.size();

        assertEquals(AudioForegroundService.ACK_STALE,
                lifecycle.stop(101, 12, actions));
        assertEquals(202, lifecycle.activeSessionId());
        assertEquals(eventsBeforeStaleStop, actions.events.size());

        assertEquals(AudioForegroundService.ACK_STOPPED,
                lifecycle.stop(202, 13, actions));
        assertEquals(0, lifecycle.activeSessionId());
        assertTrue(actions.events.contains("stopForeground"));
        assertTrue(actions.events.contains("stopSelfResult:13"));

        actions.events.clear();
        assertEquals(AudioForegroundService.ACK_STOPPED,
                lifecycle.stop(999, 14, actions));
        assertEquals("no-active STOP did not terminate the service command",
                2, actions.events.size());
        assertTrue(actions.events.contains("stopSelfResult:14"));
    }

    @Test
    public void staleStartCannotReplaceANewerSession() {
        AudioForegroundService.SessionLifecycle lifecycle =
                new AudioForegroundService.SessionLifecycle();
        FakeActions actions = new FakeActions();

        assertEquals(AudioForegroundService.ACK_STARTED,
                lifecycle.start(202, 20, actions));
        assertEquals(AudioForegroundService.ACK_STALE,
                lifecycle.start(101, 19, actions));
        assertEquals(202, lifecycle.activeSessionId());
    }

    @Test
    public void startForegroundFailureClearsTheSessionAndReportsFailure() {
        AudioForegroundService.SessionLifecycle lifecycle =
                new AudioForegroundService.SessionLifecycle();
        FakeActions actions = new FakeActions();
        actions.failStartForeground = true;

        assertEquals(AudioForegroundService.ACK_FAILED,
                lifecycle.start(303, 30, actions));
        assertEquals(0, lifecycle.activeSessionId());
        assertTrue(actions.events.contains("startForeground"));
        assertTrue(actions.events.contains("stopForeground"));
        assertTrue(actions.events.contains("stopSelfResult:30"));
        assertFalse("failure path left a foreground session active",
                actions.foregroundActive);
    }

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private Ack sendRealCommand(Intent command) throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<Ack> ack = new AtomicReference<>();
        command.putExtra(AudioForegroundService.EXTRA_ACK, new ResultReceiver(
                new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                ack.set(new Ack(resultCode, resultData));
                received.countDown();
            }
        });
        if (AudioForegroundService.ACTION_STOP.equals(command.getAction())
                || isPromotionFailureTestService(command)) {
            context().startService(command);
        } else {
            ContextCompat.startForegroundService(context(), command);
        }
        assertTrue("real service did not send a ResultReceiver ACK",
                received.await(3, TimeUnit.SECONDS));
        Ack result = ack.get();
        assertTrue("real service ACK payload was missing", result != null);
        return result;
    }

    private static boolean isPromotionFailureTestService(Intent command) {
        return command.getComponent() != null
                && FailingAudioForegroundService.class.getName().equals(
                        command.getComponent().getClassName());
    }

    private static final class Ack {
        final int resultCode;
        final long sessionId;
        final int startId;
        final int stopSelfStartId;
        final boolean stopSelfResult;

        Ack(int resultCode, Bundle data) {
            this.resultCode = resultCode;
            sessionId = data.getLong(AudioForegroundService.EXTRA_ACK_SESSION_ID);
            startId = data.getInt(AudioForegroundService.EXTRA_ACK_START_ID);
            stopSelfStartId = data.getInt(AudioForegroundService.EXTRA_ACK_STOP_SELF_START_ID);
            stopSelfResult = data.getBoolean(AudioForegroundService.EXTRA_ACK_STOP_SELF_RESULT);
        }
    }

    private static final class FakeActions implements AudioForegroundService.SessionLifecycle.Actions {
        final List<String> events = new ArrayList<>();
        boolean failStartForeground;
        boolean foregroundActive;

        @Override
        public void startForeground() {
            events.add("startForeground");
            if (failStartForeground) {
                throw new IllegalStateException("injected startForeground failure");
            }
            foregroundActive = true;
        }

        @Override
        public void stopForeground() {
            events.add("stopForeground");
            foregroundActive = false;
        }

        @Override
        public boolean stopSelfResult(int startId) {
            events.add("stopSelfResult:" + startId);
            return true;
        }
    }
}
