package com.bg7yoz.ft8cn.x6100;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bg7yoz.ft8cn.connector.X6100Connector;
import com.bg7yoz.ft8cn.util.SubmissionResult;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class X6100CommandSubmissionTest {
    @Test
    public void inactiveControlSessionDoesNotAdvanceCommandOrPttState() {
        X6100Radio radio = new X6100Radio();
        int sequence = radio.getCommandSequence();
        assertEquals(SubmissionResult.SESSION_INACTIVE,
                radio.sendCommand(X6100Radio.XieguCommand.PTT, "ptt on"));
        assertEquals(sequence, radio.getCommandSequence());

        X6100Connector connector = new X6100Connector(
                ApplicationProvider.getApplicationContext(), radio, 0);
        connector.setPttOn(true);
        assertFalse(radio.isPttOn);
    }
}
