package com.gatepass.email;

import com.gatepass.model.GatePass;
import com.gatepass.model.Parent;
import com.gatepass.util.DateTimeUtil;
import java.time.Duration;

/**
 * The text of the two parent emails. Kept apart from {@link EmailService} so the wording can be
 * read, changed and tested without a mail server.
 */
public final class EmailTemplates {

    private static final String FOOTER = """

            This is an automatic message from the college Digital Gate Pass system.
            Please contact the hostel warden if you have any questions. Do not reply to this email.
            """;

    private EmailTemplates() {
    }

    public static String exitSubject(GatePass pass) {
        return "Campus Exit Notification - " + pass.getCollegeId();
    }

    public static String exitBody(GatePass pass, Parent parent) {
        return "Dear " + parent.getName() + ",\n\n"
                + "This is to inform you that your ward has LEFT the college campus.\n\n"
                + "Student name       : " + pass.getStudent().getName() + "\n"
                + "College ID         : " + pass.getCollegeId() + "\n"
                + "Gate pass number   : " + pass.getPassNo() + "\n"
                + "Actual exit time   : " + DateTimeUtil.display(pass.getActualExitDateTime()) + "\n"
                + "Expected return    : " + DateTimeUtil.display(pass.getExpectedEntryDateTime()) + "\n"
                + "Reason for leaving : " + pass.getReason() + "\n"
                + FOOTER;
    }

    public static String returnSubject(GatePass pass) {
        return "Campus Return Notification - " + pass.getCollegeId();
    }

    public static String returnBody(GatePass pass, Parent parent) {
        return "Dear " + parent.getName() + ",\n\n"
                + "This is to inform you that your ward has RETURNED to the college campus.\n\n"
                + "Student name       : " + pass.getStudent().getName() + "\n"
                + "College ID         : " + pass.getCollegeId() + "\n"
                + "Gate pass number   : " + pass.getPassNo() + "\n"
                + "Actual return time : " + DateTimeUtil.display(pass.getActualEntryDateTime()) + "\n"
                + "Expected return    : " + DateTimeUtil.display(pass.getExpectedEntryDateTime()) + "\n"
                + "Return status      : " + returnStatus(pass) + "\n"
                + FOOTER;
    }

    /** "On time" or "LATE (by 2 h 30 min)". Uses the late flag decided by PassService. */
    static String returnStatus(GatePass pass) {
        if (!pass.isLateFlag()) {
            return "On time";
        }
        Duration lateBy = Duration.between(pass.getExpectedEntryDateTime(), pass.getActualEntryDateTime());
        return "LATE (by " + DateTimeUtil.formatDuration(lateBy) + ")";
    }
}
