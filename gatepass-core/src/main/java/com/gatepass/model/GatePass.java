package com.gatepass.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One gate pass request and everything that happened to it.
 *
 * <p>ENCAPSULATION: every field is private and there are NO setters for status, actual exit,
 * actual entry, late flag or remark. A GatePass object is a read-only snapshot. The only way
 * to change a pass is through {@code PassService}, which checks the rules and updates the
 * database; callers then read the pass again.</p>
 */
public class GatePass {

    private final String passNo;                  // GP1001, GP1002 ... (null until saved)
    private final Student student;                // who the pass belongs to
    private final String reason;
    private final LocalDateTime exitDateTime;     // requested exit
    private final LocalDateTime expectedEntryDateTime;
    private final LocalDateTime actualExitDateTime;
    private final LocalDateTime actualEntryDateTime;
    private final PassStatus status;
    private final boolean lateFlag;
    private final String rejectionRemark;
    private final LocalDateTime createdAt;

    /**
     * Constructor for a NEW request typed by a student: status starts as PENDING and all
     * movement fields are empty. The pass number and creation time are given by the database
     * layer when the pass is saved.
     */
    public GatePass(Student student, String reason,
                    LocalDateTime exitDateTime, LocalDateTime expectedEntryDateTime) {
        this(null, student, reason, exitDateTime, expectedEntryDateTime,
                null, null, PassStatus.PENDING, false, null, null);
    }

    /** Full constructor: used by the DAO to rebuild a pass that is already stored. */
    public GatePass(String passNo, Student student, String reason,
                    LocalDateTime exitDateTime, LocalDateTime expectedEntryDateTime,
                    LocalDateTime actualExitDateTime, LocalDateTime actualEntryDateTime,
                    PassStatus status, boolean lateFlag, String rejectionRemark,
                    LocalDateTime createdAt) {
        this.passNo = passNo;
        this.student = student;
        this.reason = reason;
        this.exitDateTime = exitDateTime;
        this.expectedEntryDateTime = expectedEntryDateTime;
        this.actualExitDateTime = actualExitDateTime;
        this.actualEntryDateTime = actualEntryDateTime;
        this.status = status;
        this.lateFlag = lateFlag;
        this.rejectionRemark = rejectionRemark;
        this.createdAt = createdAt;
    }

    public String getPassNo() {
        return passNo;
    }

    public Student getStudent() {
        return student;
    }

    public String getCollegeId() {
        return student.getCollegeId();
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getExitDateTime() {
        return exitDateTime;
    }

    /** Date part of the requested exit (the guard rule compares dates, not times). */
    public LocalDate getExitDate() {
        return exitDateTime.toLocalDate();
    }

    public LocalDateTime getExpectedEntryDateTime() {
        return expectedEntryDateTime;
    }

    public LocalDateTime getActualExitDateTime() {
        return actualExitDateTime;
    }

    public LocalDateTime getActualEntryDateTime() {
        return actualEntryDateTime;
    }

    public PassStatus getStatus() {
        return status;
    }

    public boolean isLateFlag() {
        return lateFlag;
    }

    public String getRejectionRemark() {
        return rejectionRemark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
