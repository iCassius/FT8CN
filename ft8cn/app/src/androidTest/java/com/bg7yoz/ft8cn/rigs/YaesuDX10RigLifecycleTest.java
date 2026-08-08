package com.bg7yoz.ft8cn.rigs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class YaesuDX10RigLifecycleTest {
    @Test
    public void disconnectingStopsPollingTimerEvenBeforeConnectorConnects() throws Exception {
        YaesuDX10Rig rig = new YaesuDX10Rig();
        Field timerField = YaesuDX10Rig.class.getDeclaredField("readFreqTimer");
        timerField.setAccessible(true);
        assertNotNull(timerField.get(rig));

        rig.onDisconnecting();

        assertNull("discarded rigs must not retain a polling Timer", timerField.get(rig));
    }
}
