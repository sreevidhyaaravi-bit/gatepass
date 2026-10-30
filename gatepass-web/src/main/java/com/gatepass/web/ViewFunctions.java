package com.gatepass.web;

import com.gatepass.model.GatePass;
import com.gatepass.util.DateTimeUtil;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Small formatting helpers the JSP pages call as EL functions, e.g. {@code ${gp:dt(p.exitDateTime)}}.
 * They are declared in WEB-INF/gatepass.tld. Formatting only, no rules.
 */
public final class ViewFunctions {

    private ViewFunctions() {
    }

    /** 30 Oct 2026, 04:05 PM  (or "-" when empty). */
    public static String dt(LocalDateTime value) {
        return DateTimeUtil.display(value);
    }

    /** 2 h 15 min */
    public static String duration(Duration value) {
        return value == null ? "-" : DateTimeUtil.formatDuration(value);
    }

    /** How late a returned student was (only meaningful when the late flag is set). */
    public static String lateBy(GatePass pass) {
        if (pass.getActualEntryDateTime() == null) {
            return "-";
        }
        return DateTimeUtil.formatDuration(
                Duration.between(pass.getExpectedEntryDateTime(), pass.getActualEntryDateTime()));
    }
}
