package edu.whut.clf.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 认领状态终态/有效态判断单元测试（状态机守卫逻辑）。 */
class ClaimStatusTest {

    @Test
    void terminalStates() {
        assertTrue(ClaimStatus.COMPLETED.isTerminal());
        assertTrue(ClaimStatus.REJECTED.isTerminal());
        assertTrue(ClaimStatus.WITHDRAWN.isTerminal());
        assertTrue(ClaimStatus.CLOSED.isTerminal());
    }

    @Test
    void activeStates() {
        assertTrue(ClaimStatus.PENDING.isActive());
        assertTrue(ClaimStatus.WAITING_HANDOVER.isActive());
        assertFalse(ClaimStatus.COMPLETED.isActive());
    }

    @Test
    void terminalNotActive() {
        for (ClaimStatus s : ClaimStatus.values()) {
            assertFalse(s.isTerminal() && s.isActive(), s + " 不能既是终态又是有效态");
        }
    }
}
