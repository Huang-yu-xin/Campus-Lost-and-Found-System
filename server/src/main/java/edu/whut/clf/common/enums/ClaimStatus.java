package edu.whut.clf.common.enums;

/** 认领申请状态。终态：COMPLETED/REJECTED/WITHDRAWN/CLOSED。 */
public enum ClaimStatus {
    PENDING, WAITING_HANDOVER, COMPLETED, REJECTED, WITHDRAWN, CLOSED;

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == WITHDRAWN || this == CLOSED;
    }

    public boolean isActive() {
        return this == PENDING || this == WAITING_HANDOVER;
    }
}
