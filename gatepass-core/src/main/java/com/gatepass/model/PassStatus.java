package com.gatepass.model;

/**
 * Life cycle of a gate pass.
 *
 * <pre>
 *   PENDING --approve--> APPROVED --exit--> EXITED --entry--> EXPIRED
 *      |                     |
 *      +--reject--> REJECTED +--(exit date passed, never used)--> EXPIRED
 * </pre>
 *
 * REJECTED and EXPIRED are final: a pass can never be reused.
 */
public enum PassStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXITED,
    EXPIRED;

    /** True when the status model allows moving from this status to {@code next}. */
    public boolean canMoveTo(PassStatus next) {
        return switch (this) {
            case PENDING -> next == APPROVED || next == REJECTED;
            case APPROVED -> next == EXITED || next == EXPIRED;
            case EXITED -> next == EXPIRED;
            case REJECTED, EXPIRED -> false;
        };
    }
}
