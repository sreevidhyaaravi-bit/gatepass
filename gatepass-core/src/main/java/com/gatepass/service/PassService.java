package com.gatepass.service;

import com.gatepass.dao.GatePassDAO;
import com.gatepass.dao.UserDAO;
import com.gatepass.model.GatePass;
import com.gatepass.model.Parent;
import com.gatepass.model.PassStatus;
import com.gatepass.model.Role;
import com.gatepass.model.Student;
import com.gatepass.model.User;
import com.gatepass.util.AppConfig;
import com.gatepass.util.DateTimeUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * THE place where every business rule of the gate pass system lives.
 *
 * <p>Servlets (website) and Swing buttons (guard app) only call these methods; they never
 * repeat the rules. Every method that acts for a person receives the logged-in {@link User}
 * ("actor") and refuses if that person's role may not do the action.</p>
 *
 * <p>Layers: UI -> PassService (rules) -> DAO (SQL) -> SQLite.</p>
 */
public class PassService {

    private static final Logger LOG = Logger.getLogger(PassService.class.getName());
    private static final int MAX_REASON_LENGTH = 300;
    private static final int MAX_REMARK_LENGTH = 200;
    private static final int STUDENT_HISTORY_LIMIT = 10;
    private static final int MOVEMENT_LIST_LIMIT = 100;

    private final UserDAO userDAO;
    private final GatePassDAO gatePassDAO;
    private final MovementNotifier notifier;
    private final Clock clock;

    /** Normal setup used by the real applications: system clock in the campus timezone. */
    public PassService(MovementNotifier notifier) {
        this(new UserDAO(), new GatePassDAO(), notifier, Clock.system(AppConfig.CAMPUS_ZONE));
    }

    public PassService() {
        this(MovementNotifier.NONE);
    }

    /** Full constructor: lets tests supply a fake clock and fake DAOs. */
    public PassService(UserDAO userDAO, GatePassDAO gatePassDAO, MovementNotifier notifier, Clock clock) {
        this.userDAO = userDAO;
        this.gatePassDAO = gatePassDAO;
        this.notifier = notifier;
        this.clock = clock;
    }

    // ================================================================= student actions

    /**
     * A student applies for a pass. Only the reason and the two date-times come from the form;
     * name, class, semester and hostel roll are read from the database for the logged-in student.
     *
     * @param exitText   requested exit, e.g. "2026-11-02T09:00" (as sent by the browser)
     * @param returnText expected return, same format
     * @throws BusinessRuleException with a clear message when validation fails
     */
    public GatePass applyForPass(User actor, String reason, String exitText, String returnText) {
        requireRole(actor, Role.STUDENT);

        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new BusinessRuleException("Please enter the reason for leaving.");
        }
        if (cleanReason.length() > MAX_REASON_LENGTH) {
            throw new BusinessRuleException("The reason is too long (maximum " + MAX_REASON_LENGTH + " characters).");
        }
        if (exitText == null || exitText.isBlank()) {
            throw new BusinessRuleException("Please enter the exit date and time.");
        }
        if (returnText == null || returnText.isBlank()) {
            throw new BusinessRuleException("Please enter the expected return date and time.");
        }
        LocalDateTime exit = parseOrThrow(exitText, "exit");
        LocalDateTime expectedReturn = parseOrThrow(returnText, "return");

        if (exit.toLocalDate().isBefore(today())) {
            throw new BusinessRuleException("The exit date cannot be in the past.");
        }
        if (!expectedReturn.isAfter(exit)) {
            throw new BusinessRuleException("The expected return must be later than the exit time.");
        }

        // Always load the student from the database using the LOGGED-IN id, never a form value.
        Student student = userDAO.findStudent(actor.getUserId())
                .orElseThrow(() -> new BusinessRuleException("Your student record was not found."));
        return gatePassDAO.create(student, cleanReason, exit, expectedReturn, now());
    }

    /** The student's own profile (including parent details). */
    public Student getMyProfile(User actor) {
        requireRole(actor, Role.STUDENT);
        return userDAO.findStudent(actor.getUserId())
                .orElseThrow(() -> new BusinessRuleException("Your student record was not found."));
    }

    /** The student's own last 10 passes, newest first. */
    public List<GatePass> getMyRecentPasses(User actor) {
        requireRole(actor, Role.STUDENT);
        expireUnusedPasses();
        return gatePassDAO.findByStudent(actor.getUserId(), STUDENT_HISTORY_LIMIT);
    }

    // ================================================================= warden actions

    public List<GatePass> getPendingRequests(User actor) {
        requireRole(actor, Role.WARDEN);
        return gatePassDAO.findByStatus(PassStatus.PENDING);
    }

    public GatePass approvePass(User actor, String passNo) {
        requireRole(actor, Role.WARDEN);
        GatePass pass = loadPass(passNo);
        requirePending(pass);
        if (!gatePassDAO.approve(pass.getPassNo())) {
            throw new BusinessRuleException("This pass was already decided by someone else. Please refresh.");
        }
        return loadPass(passNo);
    }

    public GatePass rejectPass(User actor, String passNo, String remark) {
        requireRole(actor, Role.WARDEN);
        String cleanRemark = remark == null ? "" : remark.trim();
        if (cleanRemark.isEmpty()) {
            throw new BusinessRuleException("A rejection remark is required.");
        }
        if (cleanRemark.length() > MAX_REMARK_LENGTH) {
            throw new BusinessRuleException("The remark is too long (maximum " + MAX_REMARK_LENGTH + " characters).");
        }
        GatePass pass = loadPass(passNo);
        requirePending(pass);
        if (!gatePassDAO.reject(pass.getPassNo(), cleanRemark)) {
            throw new BusinessRuleException("This pass was already decided by someone else. Please refresh.");
        }
        return loadPass(passNo);
    }

    /** Gate movements (anyone with an actual exit), latest first. */
    public List<GatePass> getMovementUpdates(User actor) {
        requireRole(actor, Role.WARDEN);
        expireUnusedPasses();
        return gatePassDAO.findMovements(MOVEMENT_LIST_LIMIT);
    }

    /** Passes returned after the expected time. */
    public List<GatePass> getLateReturns(User actor) {
        requireRole(actor, Role.WARDEN);
        return gatePassDAO.findLateReturns();
    }

    /**
     * Students who are still outside and past the expected return time: status EXITED, no actual
     * entry, and expected entry earlier than now. A student who already returned (late or not)
     * has status EXPIRED, so they can never appear here.
     */
    public List<OverdueEntry> getOverdueList(User actor) {
        requireRole(actor, Role.WARDEN);
        LocalDateTime now = now();
        List<OverdueEntry> overdue = new ArrayList<>();
        for (GatePass pass : gatePassDAO.findByStatus(PassStatus.EXITED)) {
            if (pass.getActualEntryDateTime() == null && pass.getExpectedEntryDateTime().isBefore(now)) {
                overdue.add(new OverdueEntry(pass, Duration.between(pass.getExpectedEntryDateTime(), now)));
            }
        }
        overdue.sort(Comparator.comparing(OverdueEntry::overdueBy).reversed());
        return overdue;
    }

    /** Student profile (with parent contact) for the warden's search box. */
    public Optional<Student> findStudentProfile(User actor, String collegeId) {
        requireRole(actor, Role.WARDEN);
        return userDAO.findStudent(collegeId);
    }

    /** Complete pass history of one student. */
    public List<GatePass> getStudentHistory(User actor, String collegeId) {
        requireRole(actor, Role.WARDEN);
        Student student = userDAO.findStudent(collegeId)
                .orElseThrow(() -> new BusinessRuleException("Student not found"));
        expireUnusedPasses();
        return gatePassDAO.findByStudent(student.getCollegeId(), 0);
    }

    // ================================================================= guard actions

    /** Searches a pass for the guard. Applies automatic expiry first. Empty = "Pass not found". */
    public Optional<GuardPassView> searchPass(User actor, String passNo) {
        requireRole(actor, Role.GUARD);
        Optional<GatePass> found = gatePassDAO.findByPassNo(passNo);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(GuardPassView.from(expireIfNeeded(found.get())));
    }

    /** The guard's movement table: latest exits and entries (limited fields only). */
    public List<GuardPassView> getGateMovements(User actor) {
        requireRole(actor, Role.GUARD);
        expireUnusedPasses();
        List<GuardPassView> views = new ArrayList<>();
        for (GatePass pass : gatePassDAO.findMovements(MOVEMENT_LIST_LIMIT)) {
            views.add(GuardPassView.from(pass));
        }
        return views;
    }

    /**
     * Records that the student has LEFT the campus.
     *
     * <p>Allowed only when: the pass exists, is APPROVED, and its exit DATE is today (time of day
     * does not matter). The database change is saved first; only then the parent notification
     * is started in the background.</p>
     */
    public MovementResult markExit(User actor, String passNo) {
        requireRole(actor, Role.GUARD);
        GatePass pass = expireIfNeeded(loadPass(passNo));

        switch (pass.getStatus()) {
            case APPROVED -> { /* the only status that may exit; continue below */ }
            case PENDING -> throw new BusinessRuleException("Exit refused: this pass has not been approved yet.");
            case REJECTED -> throw new BusinessRuleException("Exit refused: this pass was rejected.");
            case EXITED -> throw new BusinessRuleException("Exit refused: the exit was already recorded at "
                    + DateTimeUtil.format(pass.getActualExitDateTime()) + ".");
            case EXPIRED -> throw new BusinessRuleException("Exit refused: this pass has expired or was already used.");
        }
        if (!pass.getExitDate().equals(today())) {
            throw new BusinessRuleException("Exit refused: this pass is valid for exit only on "
                    + pass.getExitDate() + " (today is " + today() + ").");
        }

        LocalDateTime actualExit = now();
        // Conditional update: if two guards click at once, only one UPDATE succeeds.
        if (!gatePassDAO.recordExit(pass.getPassNo(), actualExit)) {
            throw new BusinessRuleException("Exit refused: this pass was just updated by another gate operation. Search again.");
        }

        GatePass saved = loadPass(pass.getPassNo());               // data is now committed
        CompletableFuture<EmailResult> email = notifyExit(saved);   // only after the commit
        return new MovementResult(GuardPassView.from(saved),
                "Exit recorded at " + DateTimeUtil.format(actualExit) + ".", email);
    }

    /**
     * Records that the student has come BACK.
     *
     * <p>Allowed only when the pass is EXITED. The pass becomes EXPIRED (single use). The late
     * flag is set when the actual entry is after the expected entry; early or on-time entries
     * leave it false.</p>
     */
    public MovementResult markEntry(User actor, String passNo) {
        requireRole(actor, Role.GUARD);
        GatePass pass = expireIfNeeded(loadPass(passNo));

        switch (pass.getStatus()) {
            case EXITED -> { /* the only status that may enter; continue below */ }
            case PENDING, APPROVED -> throw new BusinessRuleException("Entry refused: the student has not exited with this pass.");
            case REJECTED -> throw new BusinessRuleException("Entry refused: this pass was rejected.");
            case EXPIRED -> throw new BusinessRuleException(pass.getActualEntryDateTime() != null
                    ? "Entry refused: the return was already recorded at "
                    + DateTimeUtil.format(pass.getActualEntryDateTime()) + "."
                    : "Entry refused: this pass has expired.");
        }

        LocalDateTime actualEntry = now();
        boolean late = isLateReturn(pass.getExpectedEntryDateTime(), actualEntry);
        if (!gatePassDAO.recordEntry(pass.getPassNo(), actualEntry, late)) {
            throw new BusinessRuleException("Entry refused: this pass was just updated by another gate operation. Search again.");
        }

        GatePass saved = loadPass(pass.getPassNo());
        CompletableFuture<EmailResult> email = notifyEntry(saved);
        return new MovementResult(GuardPassView.from(saved),
                "Entry recorded at " + DateTimeUtil.format(actualEntry)
                        + (late ? " (LATE return)." : " (on time)."), email);
    }

    // ================================================================= expiry and shared rules

    /**
     * Automatic expiry WITHOUT a background job: called whenever passes are searched or listed.
     * An APPROVED pass whose exit date is before today and that never exited becomes EXPIRED.
     * EXITED passes are never touched, because the student is still outside.
     *
     * @return how many passes were expired
     */
    public int expireUnusedPasses() {
        int expired = 0;
        for (GatePass pass : gatePassDAO.findByStatus(PassStatus.APPROVED)) {
            if (isExpiredUnused(pass) && gatePassDAO.expireUnused(pass.getPassNo())) {
                expired++;
            }
        }
        return expired;
    }

    /** Single-pass version of expiry; returns the pass as it is after the check. */
    private GatePass expireIfNeeded(GatePass pass) {
        if (isExpiredUnused(pass)) {
            gatePassDAO.expireUnused(pass.getPassNo());
            return loadPass(pass.getPassNo());
        }
        return pass;
    }

    private boolean isExpiredUnused(GatePass pass) {
        return pass.getStatus() == PassStatus.APPROVED
                && pass.getActualExitDateTime() == null
                && pass.getExitDate().isBefore(today());
    }

    /** Late means strictly after the expected time. Early or exactly on time is not late. */
    public static boolean isLateReturn(LocalDateTime expectedEntry, LocalDateTime actualEntry) {
        return actualEntry.isAfter(expectedEntry);
    }

    // ================================================================= private helpers

    private void requireRole(User actor, Role required) {
        if (actor == null || actor.getRole() != required) {
            throw new AccessDeniedException();
        }
    }

    private void requirePending(GatePass pass) {
        if (pass.getStatus() != PassStatus.PENDING) {
            throw new BusinessRuleException("Only pending passes can be approved or rejected (this pass is "
                    + pass.getStatus() + ").");
        }
    }

    private GatePass loadPass(String passNo) {
        return gatePassDAO.findByPassNo(passNo)
                .orElseThrow(() -> new BusinessRuleException("Pass not found"));
    }

    private LocalDateTime parseOrThrow(String text, String which) {
        try {
            return DateTimeUtil.parseInput(text);
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("The " + which + " date and time is not valid.");
        }
    }

    /** Current campus time, cut to whole minutes because the database stores minutes only. */
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private CompletableFuture<EmailResult> notifyExit(GatePass saved) {
        return notifySafely(saved, true);
    }

    private CompletableFuture<EmailResult> notifyEntry(GatePass saved) {
        return notifySafely(saved, false);
    }

    /**
     * Starts the parent email. Whatever goes wrong here is reported in the EmailResult; it never
     * throws and never undoes the saved movement.
     */
    private CompletableFuture<EmailResult> notifySafely(GatePass saved, boolean exit) {
        try {
            Optional<Parent> parent = userDAO.findParentByCollegeId(saved.getCollegeId());
            if (parent.isEmpty()) {
                return CompletableFuture.completedFuture(EmailResult.notSent("No parent is registered for this student."));
            }
            CompletableFuture<EmailResult> future = exit
                    ? notifier.notifyExit(saved, parent.get())
                    : notifier.notifyEntry(saved, parent.get());
            return future.exceptionally(error ->
                    EmailResult.notSent("The notification could not be sent."));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Parent notification failed for pass " + saved.getPassNo(), e);
            return CompletableFuture.completedFuture(EmailResult.notSent("The notification could not be sent."));
        }
    }
}
