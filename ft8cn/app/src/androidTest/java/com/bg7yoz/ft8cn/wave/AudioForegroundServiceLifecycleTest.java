package com.bg7yoz.ft8cn.wave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises the service command state machine without changing system permissions. */
@RunWith(AndroidJUnit4.class)
public class AudioForegroundServiceLifecycleTest {
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
