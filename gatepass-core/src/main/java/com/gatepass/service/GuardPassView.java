package com.gatepass.service;

import com.gatepass.model.GatePass;
import com.gatepass.model.PassStatus;
import java.time.LocalDateTime;

/**
 * What the security guard is allowed to see about a pass, and nothing more.
 *
 * <p>This is a "record": a small class that only carries data. It has NO parent, NO email, NO
 * reason and NO password fields, so even a bug in the Swing screen cannot show them: the data
 * simply is not there. (Access control enforced by the service layer, not by hiding labels.)</p>
 */
public record GuardPassView(
        String passNo,
        String studentName,
        String collegeId,
        String className,
        int semester,
        String hostelRoll,
        LocalDateTime exitDateTime,
        LocalDateTime expectedEntryDateTime,
        LocalDateTime actualExitDateTime,
        LocalDateTime actualEntryDateTime,
        PassStatus status,
        boolean lateFlag) {

    public static GuardPassView from(GatePass pass) {
        return new GuardPassView(
                pass.getPassNo(),
                pass.getStudent().getName(),
                pass.getCollegeId(),
                pass.getStudent().getClassName(),
                pass.getStudent().getSemester(),
                pass.getStudent().getHostelRoll(),
                pass.getExitDateTime(),
                pass.getExpectedEntryDateTime(),
                pass.getActualExitDateTime(),
                pass.getActualEntryDateTime(),
                pass.getStatus(),
                pass.isLateFlag());
    }

    /** UI hint only: the Mark Exit button may be enabled. PassService re-checks everything. */
    public boolean canMarkExit() {
        return status == PassStatus.APPROVED;
    }

    /** UI hint only: the Mark Entry button may be enabled. PassService re-checks everything. */
    public boolean canMarkEntry() {
        return status == PassStatus.EXITED;
    }
}
