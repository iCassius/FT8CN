package com.bg7yoz.ft8cn.rigs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.connector.ConnectMode;
import com.bg7yoz.ft8cn.connector.BaseRigConnector;
import com.bg7yoz.ft8cn.util.SubmissionResult;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NetworkRigPttSubmissionTest {
    @Test
    public void x6100RigOnlyCommitsPttStateAfterEnqueuedAndKeepsRejectedOffState() {
        RecordingConnector connector = new RecordingConnector();
        XieGu6100NetRig rig = new XieGu6100NetRig(0);
        rig.setConnector(connector);

        connector.pttResult = SubmissionResult.ENQUEUED;
        rig.setPTT(true);
        assertTrue(rig.isPttOn());

        connector.pttResult = SubmissionResult.REJECTED;
        rig.setPTT(false);
        assertTrue("a rejected OFF must preserve the confirmed local PTT state", rig.isPttOn());
        assertEquals(SubmissionResult.REJECTED, connector.getLastOperationSubmission());
    }

    @Test
    public void flexRigDoesNotAdvanceCommandOrPttStateWhenSubmissionIsRejected() {
        RecordingConnector connector = new RecordingConnector();
        connector.connected = true;
        FlexNetworkRig rig = new FlexNetworkRig();
        rig.setConnector(connector);

        connector.dataResult = SubmissionResult.REJECTED;
        int sequence = rig.getCommandSequence();
        rig.commandSliceTune(0, "14.074");
        assertEquals(SubmissionResult.REJECTED, connector.getLastOperationSubmission());
        assertEquals(sequence, rig.getCommandSequence());
        assertEquals(null, rig.getLastCommand());
        rig.setPTT(true);
        assertFalse(rig.isPttOn());
    }

    @Test
    public void icomNetworkPttOnlyChangesBaseStateAndEventAfterBothSubmissionsSucceed() {
        int oldConnectMode = GeneralVariables.connectMode;
        try {
            GeneralVariables.connectMode = ConnectMode.NETWORK;
            RecordingConnector connector = new RecordingConnector();
            PttEvents events = new PttEvents();
            IcomRig rig = new IcomRig(0, true, false);
            rig.setConnector(connector);
            rig.setOnRigStateChanged(events);

            connector.dataResult = SubmissionResult.REJECTED;
            connector.pttResult = SubmissionResult.ENQUEUED;
            rig.setPTT(true);
            assertFalse(rig.isPttOn());
            assertEquals(0, connector.pttCalls);
            assertEquals(0, events.count);

            connector.dataResult = SubmissionResult.ENQUEUED;
            connector.pttResult = SubmissionResult.REJECTED;
            rig.setPTT(true);
            assertFalse(rig.isPttOn());
            assertEquals(0, events.count);

            connector.pttResult = SubmissionResult.ENQUEUED;
            rig.setPTT(true);
            assertTrue(rig.isPttOn());
            assertEquals(1, events.count);

            connector.pttResult = SubmissionResult.REJECTED;
            rig.setPTT(false);
            assertTrue(rig.isPttOn());
            assertEquals(1, events.count);
        } finally {
            GeneralVariables.connectMode = oldConnectMode;
        }
    }

    @Test
    public void xieGu6100NetworkPttRejectedOnAndOffDoNotChangeBaseStateOrEvent() {
        int oldConnectMode = GeneralVariables.connectMode;
        try {
            GeneralVariables.connectMode = ConnectMode.NETWORK;
            RecordingConnector connector = new RecordingConnector();
            PttEvents events = new PttEvents();
            XieGu6100Rig rig = new XieGu6100Rig(0, false);
            rig.setConnector(connector);
            rig.setOnRigStateChanged(events);

            connector.pttResult = SubmissionResult.REJECTED;
            rig.setPTT(true);
            assertFalse(rig.isPttOn());
            assertEquals(0, events.count);

            connector.pttResult = SubmissionResult.ENQUEUED;
            rig.setPTT(true);
            assertTrue(rig.isPttOn());
            assertEquals(1, events.count);

            connector.pttResult = SubmissionResult.REJECTED;
            rig.setPTT(false);
            assertTrue(rig.isPttOn());
            assertEquals(1, events.count);
        } finally {
            GeneralVariables.connectMode = oldConnectMode;
        }
    }

    private static final class RecordingConnector extends BaseRigConnector {
        SubmissionResult dataResult = SubmissionResult.SESSION_INACTIVE;
        SubmissionResult pttResult = SubmissionResult.SESSION_INACTIVE;
        int pttCalls;
        boolean connected;

        RecordingConnector() {
            super(0);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public synchronized SubmissionResult submitData(byte[] data) {
            reportOperationSubmission("test data", dataResult);
            return dataResult;
        }

        @Override
        public SubmissionResult submitPttOn(boolean on) {
            pttCalls++;
            reportOperationSubmission("test ptt", pttResult);
            return pttResult;
        }
    }

    private static final class PttEvents implements OnRigStateChanged {
        int count;

        @Override public void onDisconnected() { }
        @Override public void onConnected() { }
        @Override public void onPttChanged(boolean isOn) { count++; }
        @Override public void onFreqChanged(long freq) { }
        @Override public void onRunError(String message) { }
    }
}
