package com.bg7yoz.ft8cn.util;

/** Result of handing a network/audio operation to its local transport queue. */
public enum SubmissionResult {
    /** The immutable operation snapshot is owned by the transport queue. */
    ENQUEUED,
    /** The client/session was already disconnected, closed, or not ready. */
    SESSION_INACTIVE,
    /** The bounded queue rejected the operation. It was not sent and is not retried. */
    REJECTED,
    /** The caller did not provide a usable operation. */
    INVALID_ARGUMENT;

    public boolean isEnqueued() {
        return this == ENQUEUED;
    }
}
