package com.gatepass.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gatepass.dao.GatePassDAO;
import com.gatepass.dao.UserDAO;
import com.gatepass.db.DBConnection;
import com.gatepass.db.DatabaseInitializer;
import com.gatepass.model.GatePass;
import com.gatepass.model.PassStatus;
import com.gatepass.model.Role;
import com.gatepass.model.Student;
import com.gatepass.model.User;
import com.gatepass.util.AppConfig;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the real stack: PassService -> DAO -> a real SQLite file. "Today" is fixed at
 * 2026-10-30 10:00 so the 7 demo passes (GP1001..GP1007) have known dates.
 */
class PassServiceDbTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @TempDir
    static Path tempDir;

    static TestClock clock;
    static RecordingNotifier notifier;
    static PassService service;
    static AuthService auth;

    static User student1;   // CS24001 Ananya
    static User student2;   // CS24002 Rahul
    static User warden;
    static User guard;

    @BeforeAll
    static void createDatabase() {
        DBConnection.setDatabasePath(tempDir.resolve("gatepass-test.db"));
        clock = new TestClock(AppConfig.CAMPUS_ZONE, TODAY.atTime(10, 0));
        notifier = new RecordingNotifier();
        service = new PassService(new UserDAO(), new GatePassDAO(), notifier, clock);
        auth = new AuthService();

        DatabaseInitializer.resetEverything(clock);
        student1 = auth.login("CS24001", "Student@123", Role.STUDENT).orElseThrow();
        student2 = auth.login("CS24002", "Student@123", Role.STUDENT).orElseThrow();
        warden = auth.login("WARDEN01", "Warden@123", Role.WARDEN).orElseThrow();
        guard = auth.login("GUARD01", "Guard@123", Role.GUARD).orElseThrow();
    }

    @BeforeEach
    void freshDemoPasses() {
        clock.set(TODAY.atTime(10, 0));
        DatabaseInitializer.refreshDemoPasses(clock);
        notifier.reset();
    }

    private GatePass pass(String passNo) {
        return new GatePassDAO().findByPassNo(passNo).orElseThrow();
    }

    // ------------------------------------------------------------ login and passwords

    @Test
    void loginWorksForTheRightRoleOnly() {
        assertTrue(auth.login("cs24001", "Student@123", Role.STUDENT).isPresent(), "id is not case sensitive");
        assertTrue(auth.login("CS24001", "wrong", Role.STUDENT).isEmpty());
        assertTrue(auth.login("NOBODY", "Student@123", Role.STUDENT).isEmpty());
        // valid accounts, wrong application:
        assertTrue(auth.login("CS24001", "Student@123", Role.GUARD).isEmpty(), "student cannot use guard app");
        assertTrue(auth.login("WARDEN01", "Warden@123", Role.GUARD).isEmpty(), "warden cannot use guard app");
        assertTrue(auth.login("GUARD01", "Guard@123", Role.STUDENT, Role.WARDEN).isEmpty(), "guard cannot use website");
        assertEquals("student-dashboard", student1.showDashboard());
        assertEquals("warden-dashboard", warden.showDashboard());
    }

    @Test
    void passwordsAreStoredOnlyAsSaltedHashes() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT user_id, password_hash FROM users ORDER BY user_id")) {
            List<String> hashes = new ArrayList<>();
            while (rs.next()) {
                String stored = rs.getString("password_hash");
                assertTrue(stored.startsWith("pbkdf2$"));
                assertFalse(stored.contains("Student@123") || stored.contains("Warden@123") || stored.contains("Guard@123"));
                hashes.add(stored);
            }
            assertEquals(5, hashes.size());
            assertEquals(5, hashes.stream().distinct().count(), "salt makes every stored hash different");
        }
    }

    // ------------------------------------------------------------ seed data

    @Test
    void seedDataMatchesTheDemoPlanAndIsNotDuplicated() throws Exception {
        DatabaseInitializer.initialize(clock); // second start: must not insert again
        assertEquals(PassStatus.PENDING, pass("GP1001").getStatus());
        assertEquals(PassStatus.APPROVED, pass("GP1002").getStatus());
        assertEquals(TODAY, pass("GP1002").getExitDate());
        assertEquals(PassStatus.EXITED, pass("GP1003").getStatus());
        assertTrue(pass("GP1004").isLateFlag());
        assertEquals(PassStatus.APPROVED, pass("GP1005").getStatus());
        assertTrue(pass("GP1005").getExitDate().isBefore(TODAY));
        assertEquals(PassStatus.REJECTED, pass("GP1006").getStatus());
        assertTrue(pass("GP1006").getRejectionRemark() != null);
        assertFalse(pass("GP1007").isLateFlag());
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                assertEquals(5, rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM gate_passes")) {
                rs.next();
                assertEquals(7, rs.getInt(1));
            }
        }
    }

    // ------------------------------------------------------------ applying

    @Test
    void studentAppliesAndDataComesFromTheDatabase() {
        GatePass created = service.applyForPass(student1, "  Dentist  ", "2026-10-31T09:00", "2026-10-31T18:00");
        assertEquals("GP1008", created.getPassNo());
        assertEquals(PassStatus.PENDING, created.getStatus());
        assertEquals("Dentist", created.getReason());
        assertEquals("Ananya Nair", created.getStudent().getName());
        assertEquals("H24001", created.getStudent().getHostelRoll());
        assertEquals(null, created.getActualExitDateTime());
    }

    @Test
    void applicationValidationMessages() {
        assertThrows(BusinessRuleException.class, () -> service.applyForPass(student1, " ", "2026-10-31T09:00", "2026-10-31T18:00"));
        assertThrows(BusinessRuleException.class, () -> service.applyForPass(student1, "x", "", "2026-10-31T18:00"));
        assertThrows(BusinessRuleException.class, () -> service.applyForPass(student1, "x", "2026-10-31T09:00", null));
        assertThrows(BusinessRuleException.class, () -> service.applyForPass(student1, "x", "not-a-date", "2026-10-31T18:00"));
        assertThrows(BusinessRuleException.class, () -> service.applyForPass(student1, "x", "2026-02-30T09:00", "2026-03-01T18:00"));
        assertThrows(BusinessRuleException.class, () -> service.applyForPass(student1, "x", "2026-10-29T09:00", "2026-10-31T18:00"), "past exit date");
        assertThrows(BusinessRuleException.class, () -> service.applyForPass(student1, "x", "2026-10-31T09:00", "2026-10-31T09:00"), "return not after exit");
        assertThrows(AccessDeniedException.class, () -> service.applyForPass(warden, "x", "2026-10-31T09:00", "2026-10-31T18:00"));
        assertThrows(AccessDeniedException.class, () -> service.applyForPass(guard, "x", "2026-10-31T09:00", "2026-10-31T18:00"));
    }

    @Test
    void studentSeesOnlyOwnPasses() {
        List<GatePass> mine = service.getMyRecentPasses(student2);
        assertFalse(mine.isEmpty());
        assertTrue(mine.stream().allMatch(p -> p.getCollegeId().equals("CS24002")));
        assertThrows(AccessDeniedException.class, () -> service.getMyRecentPasses(warden));
    }

    // ------------------------------------------------------------ warden decisions

    @Test
    void approveAndRejectRules() {
        assertEquals(PassStatus.APPROVED, service.approvePass(warden, "GP1001").getStatus());
        assertThrows(BusinessRuleException.class, () -> service.approvePass(warden, "GP1001"), "already approved");
        assertThrows(BusinessRuleException.class, () -> service.approvePass(warden, "GP1002"), "not pending");
        assertThrows(AccessDeniedException.class, () -> service.approvePass(student1, "GP1001"));
        assertThrows(AccessDeniedException.class, () -> service.approvePass(guard, "GP1001"));
        assertThrows(BusinessRuleException.class, () -> service.approvePass(warden, "GP9999"), "unknown pass");
    }

    @Test
    void rejectionNeedsARemark() {
        assertThrows(BusinessRuleException.class, () -> service.rejectPass(warden, "GP1001", "   "));
        assertEquals(PassStatus.PENDING, pass("GP1001").getStatus(), "failed rejection changed nothing");
        GatePass rejected = service.rejectPass(warden, "GP1001", "Exams are near");
        assertEquals(PassStatus.REJECTED, rejected.getStatus());
        assertEquals("Exams are near", rejected.getRejectionRemark());
    }

    // ------------------------------------------------------------ guard: exit

    @Test
    void exitWorksOnTheApprovedDateAndSendsOneEmail() {
        MovementResult result = service.markExit(guard, "GP1002");
        assertEquals(PassStatus.EXITED, result.pass().status());
        assertEquals(TODAY.atTime(10, 0), result.pass().actualExitDateTime());
        assertEquals(PassStatus.EXITED, pass("GP1002").getStatus(), "saved in the database");
        assertEquals(1, notifier.exitCalls.get());
    }

    @Test
    void exitIsRefusedWhenNotAllowedAndSendsNoEmail() {
        assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP1001"), "before approval");
        assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP1006"), "rejected");
        assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP1005"), "old unused pass");
        assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP1004"), "already used");
        assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP0000"), "unknown");
        assertThrows(AccessDeniedException.class, () -> service.markExit(warden, "GP1002"));
        assertEquals(0, notifier.exitCalls.get());
    }

    @Test
    void exitOnTheWrongDateIsRefused() {
        service.approvePass(warden, "GP1001"); // GP1001 is for tomorrow
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP1001"));
        assertTrue(e.getMessage().contains("valid for exit only on"));
        assertEquals(PassStatus.APPROVED, pass("GP1001").getStatus());
    }

    @Test
    void duplicateExitIsRefused() {
        service.markExit(guard, "GP1002");
        assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP1002"));
        assertEquals(1, notifier.exitCalls.get(), "no second email");
    }

    // ------------------------------------------------------------ guard: entry

    @Test
    void entryBeforeExitIsRefused() {
        assertThrows(BusinessRuleException.class, () -> service.markEntry(guard, "GP1002"));
        assertThrows(BusinessRuleException.class, () -> service.markEntry(guard, "GP1001"));
        assertEquals(0, notifier.entryCalls.get());
    }

    @Test
    void earlyReturnIsNotLate() {
        service.markExit(guard, "GP1002");            // expected return is 21:00, now is 10:00
        MovementResult result = service.markEntry(guard, "GP1002");
        assertEquals(PassStatus.EXPIRED, result.pass().status());
        assertFalse(result.pass().lateFlag());
        assertEquals(1, notifier.entryCalls.get());
    }

    @Test
    void returnExactlyOnTimeIsNotLateButOneMinuteAfterIsLate() {
        service.markExit(guard, "GP1002");
        clock.set(TODAY.atTime(21, 0));
        assertFalse(service.markEntry(guard, "GP1002").pass().lateFlag());

        DatabaseInitializer.refreshDemoPasses(clock);
        clock.set(TODAY.atTime(10, 0));
        service.markExit(guard, "GP1002");
        clock.set(TODAY.atTime(21, 1));
        assertTrue(service.markEntry(guard, "GP1002").pass().lateFlag());
    }

    @Test
    void duplicateEntryAndReuseAreRefused() {
        service.markExit(guard, "GP1002");
        service.markEntry(guard, "GP1002");
        assertThrows(BusinessRuleException.class, () -> service.markEntry(guard, "GP1002"));
        assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP1002"));
        assertEquals(1, notifier.entryCalls.get());
    }

    // ------------------------------------------------------------ overdue, late list, expiry

    @Test
    void overdueStudentLeavesTheListAfterReturningLate() {
        List<OverdueEntry> overdue = service.getOverdueList(warden);
        assertEquals(1, overdue.size());
        assertEquals("GP1003", overdue.get(0).pass().getPassNo());
        assertEquals(14 * 60, overdue.get(0).overdueBy().toMinutes()); // due yesterday 20:00, now today 10:00

        MovementResult result = service.markEntry(guard, "GP1003");
        assertTrue(result.pass().lateFlag());
        assertTrue(service.getOverdueList(warden).isEmpty());
        assertTrue(service.getLateReturns(warden).stream().anyMatch(p -> p.getPassNo().equals("GP1003")));
    }

    @Test
    void unusedApprovedPassExpiresOnLookupButExitedPassDoesNot() {
        Optional<GuardPassView> view = service.searchPass(guard, "GP1005");
        assertEquals(PassStatus.EXPIRED, view.orElseThrow().status());
        assertEquals(PassStatus.EXPIRED, pass("GP1005").getStatus(), "change is saved");
        service.expireUnusedPasses();
        assertEquals(PassStatus.EXITED, pass("GP1003").getStatus(), "student still outside stays EXITED");
        assertTrue(service.searchPass(guard, "GP4040").isEmpty());
    }

    @Test
    void expiryAlsoRunsWhenADashboardLoads() {
        service.getMyRecentPasses(student2);
        assertEquals(PassStatus.EXPIRED, pass("GP1005").getStatus());
    }

    // ------------------------------------------------------------ history, privacy

    @Test
    void wardenHistorySearch() {
        assertEquals(3, service.getStudentHistory(warden, "CS24001").size());
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> service.getStudentHistory(warden, "CS99999"));
        assertEquals("Student not found", e.getMessage());
        assertThrows(AccessDeniedException.class, () -> service.getStudentHistory(student1, "CS24002"));
        Student profile = service.findStudentProfile(warden, "CS24001").orElseThrow();
        assertEquals("parent.cs24001@example.test", profile.getParent().getEmail());
    }

    @Test
    void guardViewCarriesNoParentEmailOrReason() {
        for (var component : GuardPassView.class.getRecordComponents()) {
            String name = component.getName().toLowerCase();
            assertFalse(name.contains("mail") || name.contains("parent") || name.contains("reason") || name.contains("password"), name);
        }
        assertThrows(AccessDeniedException.class, () -> service.findStudentProfile(guard, "CS24001"));
        assertThrows(AccessDeniedException.class, () -> service.getPendingRequests(guard));
    }

    // ------------------------------------------------------------ email failure, shared DB, concurrency

    @Test
    void emailFailureNeverUndoesTheMovement() throws Exception {
        notifier.failing = true;
        MovementResult result = service.markExit(guard, "GP1002");
        assertEquals(PassStatus.EXITED, pass("GP1002").getStatus());
        assertFalse(result.email().get().submitted());

        PassService noEmail = new PassService(new UserDAO(), new GatePassDAO(), MovementNotifier.NONE, clock);
        MovementResult entry = noEmail.markEntry(guard, "GP1002");
        assertEquals(PassStatus.EXPIRED, entry.pass().status());
        assertFalse(entry.email().get().submitted(), "missing email setup is reported, not hidden");
    }

    @Test
    void anotherServiceInstanceSeesTheSameDatabase() {
        PassService other = new PassService(new UserDAO(), new GatePassDAO(), MovementNotifier.NONE, clock);
        service.markExit(guard, "GP1002");
        assertEquals(PassStatus.EXITED, other.searchPass(guard, "GP1002").orElseThrow().status());
    }

    @Test
    void onlyOneOfManySimultaneousExitsSucceeds() throws Exception {
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Callable<Boolean> task = () -> {
                try {
                    service.markExit(guard, "GP1002");
                    return true;
                } catch (BusinessRuleException e) {
                    return false;
                }
            };
            futures.add(pool.submit(task));
        }
        int successes = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                successes++;
            }
        }
        pool.shutdown();
        assertEquals(1, successes);
        assertEquals(1, notifier.exitCalls.get(), "exactly one email");
        assertNotEquals(null, pass("GP1002").getActualExitDateTime());
    }
}
