package com.bg7yoz.ft8cn.rigs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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

        connector.next = SubmissionResult.ENQUEUED;
        rig.setPTT(true);
        assertTrue(rig.isPttOn());

        connector.next = SubmissionResult.REJECTED;
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

        connector.next = SubmissionResult.REJECTED;
        int sequence = rig.getCommandSequence();
        rig.commandSliceTune(0, "14.074");
        assertEquals(SubmissionResult.REJECTED, connector.getLastOperationSubmission());
        assertEquals(sequence, rig.getCommandSequence());
        assertEquals(null, rig.getLastCommand());
        rig.setPTT(true);
        assertFalse(rig.isPttOn());
    }

    private static final class RecordingConnector extends BaseRigConnector {
        SubmissionResult next = SubmissionResult.SESSION_INACTIVE;
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
            reportOperationSubmission("test data", next);
            return next;
        }

        @Override
        public SubmissionResult submitPttOn(boolean on) {
            reportOperationSubmission("test ptt", next);
            return next;
        }
    }
}
