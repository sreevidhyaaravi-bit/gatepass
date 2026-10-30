package com.gatepass.db;

import com.gatepass.dao.DataAccessException;
import com.gatepass.dao.GatePassDAO;
import com.gatepass.dao.UserDAO;
import com.gatepass.model.PassStatus;
import com.gatepass.model.Role;
import com.gatepass.util.AppConfig;
import com.gatepass.util.PasswordUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Creates the four tables and the demo data.
 *
 * <ul>
 *   <li>{@link #initialize()} is safe to call on every start: tables use IF NOT EXISTS and demo
 *       data is inserted only when the users table is empty (so nothing is duplicated).</li>
 *   <li>{@link #refreshDemoPasses()} deletes all passes and re-creates the 7 demo passes with
 *       dates relative to TODAY. Run it before a demo day.</li>
 *   <li>{@link #resetEverything()} drops all tables and rebuilds the whole demo database.</li>
 * </ul>
 */
public final class DatabaseInitializer {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS users (
                user_id       TEXT PRIMARY KEY,
                name          TEXT NOT NULL,
                email         TEXT,
                password_hash TEXT NOT NULL,
                role          TEXT NOT NULL CHECK (role IN ('STUDENT','WARDEN','GUARD'))
            )""",
            """
            CREATE TABLE IF NOT EXISTS parents (
                parent_id INTEGER PRIMARY KEY AUTOINCREMENT,
                name      TEXT NOT NULL,
                email     TEXT NOT NULL,
                phone     TEXT
            )""",
            """
            CREATE TABLE IF NOT EXISTS students (
                college_id  TEXT PRIMARY KEY,
                user_id     TEXT NOT NULL UNIQUE REFERENCES users(user_id),
                class_name  TEXT NOT NULL,
                semester    INTEGER NOT NULL CHECK (semester BETWEEN 1 AND 8),
                hostel_roll TEXT NOT NULL UNIQUE,
                parent_id   INTEGER NOT NULL REFERENCES parents(parent_id)
            )""",
            """
            CREATE TABLE IF NOT EXISTS gate_passes (
                pass_no                 TEXT PRIMARY KEY,
                college_id              TEXT NOT NULL REFERENCES students(college_id),
                reason                  TEXT NOT NULL,
                exit_datetime           TEXT NOT NULL,
                expected_entry_datetime TEXT NOT NULL,
                actual_exit_datetime    TEXT,
                actual_entry_datetime   TEXT,
                status                  TEXT NOT NULL
                    CHECK (status IN ('PENDING','APPROVED','REJECTED','EXITED','EXPIRED')),
                late_flag               INTEGER NOT NULL DEFAULT 0 CHECK (late_flag IN (0,1)),
                rejection_remark        TEXT,
                created_at              TEXT NOT NULL
            )""",
            "CREATE INDEX IF NOT EXISTS idx_passes_student ON gate_passes(college_id)",
            "CREATE INDEX IF NOT EXISTS idx_passes_status ON gate_passes(status)"
    };

    private static final UserDAO USER_DAO = new UserDAO();
    private static final GatePassDAO PASS_DAO = new GatePassDAO();

    private DatabaseInitializer() {
    }

    // ---------------------------------------------------------------- public entry points

    public static void initialize() {
        initialize(Clock.system(AppConfig.CAMPUS_ZONE));
    }

    /** The Clock parameter lets tests pretend that "today" is a fixed day. */
    public static void initialize(Clock clock) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                createSchema(conn);
                if (!USER_DAO.anyUserExists(conn)) {
                    seedAccounts(conn);
                    seedPasses(conn, clock);
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw DataAccessException.from("preparing the database", e);
        }
    }

    public static void refreshDemoPasses() {
        refreshDemoPasses(Clock.system(AppConfig.CAMPUS_ZONE));
    }

    public static void refreshDemoPasses(Clock clock) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                PASS_DAO.deleteAll(conn);
                seedPasses(conn, clock);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw DataAccessException.from("refreshing demo passes", e);
        }
    }

    public static void resetEverything() {
        resetEverything(Clock.system(AppConfig.CAMPUS_ZONE));
    }

    public static void resetEverything(Clock clock) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS gate_passes");
                st.executeUpdate("DROP TABLE IF EXISTS students");
                st.executeUpdate("DROP TABLE IF EXISTS parents");
                st.executeUpdate("DROP TABLE IF EXISTS users");
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw DataAccessException.from("resetting the database", e);
        }
        initialize(clock);
    }

    /** Command line helper: {@code java -cp ... com.gatepass.db.DatabaseInitializer [init|refresh|reset]}. */
    public static void main(String[] args) {
        String mode = args.length == 0 ? "init" : args[0];
        switch (mode) {
            case "init" -> initialize();
            case "refresh" -> refreshDemoPasses();
            case "reset" -> resetEverything();
            default -> {
                System.err.println("Usage: DatabaseInitializer [init|refresh|reset]");
                return;
            }
        }
        System.out.println("Done (" + mode + "). Database file: " + DBConnection.getDatabasePath());
    }

    // ---------------------------------------------------------------- internals

    private static void createSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String ddl : SCHEMA) {
                st.executeUpdate(ddl);
            }
        }
    }

    /** 1 warden, 1 guard, 3 students and their parents. All demo data is fictional. */
    private static void seedAccounts(Connection conn) throws SQLException {
        USER_DAO.insertUser(conn, "WARDEN01", "Mrs. Lakshmi Pillai", "warden01@example.test",
                PasswordUtil.hash("Warden@123"), Role.WARDEN);
        USER_DAO.insertUser(conn, "GUARD01", "Mr. Suresh Kumar", null,
                PasswordUtil.hash("Guard@123"), Role.GUARD);

        // Each student gets their OWN hash (own random salt), even though the demo password is the same.
        seedStudent(conn, "CS24001", "Ananya Nair", "H24001", PasswordUtil.hash("Student@123"),
                "Mr. Rajan Nair", "parent.cs24001@example.test", "9000000001");
        seedStudent(conn, "CS24002", "Rahul Menon", "H24002", PasswordUtil.hash("Student@123"),
                "Mrs. Sheela Menon", "parent.cs24002@example.test", "9000000002");
        seedStudent(conn, "CS24003", "Meera Joseph", "H24003", PasswordUtil.hash("Student@123"),
                "Mr. Joseph Mathew", "parent.cs24003@example.test", "9000000003");
    }

    private static void seedStudent(Connection conn, String collegeId, String name, String hostelRoll,
                                    String passwordHash, String parentName, String parentEmail,
                                    String parentPhone) throws SQLException {
        int parentId = USER_DAO.insertParent(conn, parentName, parentEmail, parentPhone);
        USER_DAO.insertUser(conn, collegeId, name, collegeId.toLowerCase() + "@college.example.test",
                passwordHash, Role.STUDENT);
        USER_DAO.insertStudent(conn, collegeId, collegeId, "CSE", 3, hostelRoll, parentId);
    }

    /**
     * The 7 demo passes. Dates are built from today's date, so they stay correct on any day:
     * <pre>
     *  GP1001 PENDING            CS24001  tomorrow                  -> warden approves / rejects it
     *  GP1002 APPROVED today     CS24002  exit today                -> guard can mark exit now
     *  GP1003 EXITED + overdue   CS24003  left yesterday, due back yesterday evening
     *  GP1004 EXPIRED, late      CS24001  came back 2.5 h late
     *  GP1005 APPROVED, old      CS24002  exit date 3 days ago, never used -> expires on lookup
     *  GP1006 REJECTED + remark  CS24003
     *  GP1007 EXPIRED, on time   CS24001  came back before the expected time
     * </pre>
     */
    private static void seedPasses(Connection conn, Clock clock) throws SQLException {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

        // 1001 pending
        PASS_DAO.insertDemoPass(conn, "CS24001", "Family function at home",
                at(today.plusDays(1), 9, 0), at(today.plusDays(1), 18, 0),
                null, null, PassStatus.PENDING, false, null, now);
        // 1002 approved, exit today
        PASS_DAO.insertDemoPass(conn, "CS24002", "Dental appointment in town",
                at(today, 16, 0), at(today, 21, 0),
                null, null, PassStatus.APPROVED, false, null, now);
        // 1003 exited, expected return passed -> overdue
        PASS_DAO.insertDemoPass(conn, "CS24003", "Buy books and stationery",
                at(today.minusDays(1), 15, 0), at(today.minusDays(1), 20, 0),
                at(today.minusDays(1), 15, 10), null, PassStatus.EXITED, false, null, now);
        // 1004 returned late
        PASS_DAO.insertDemoPass(conn, "CS24001", "Visit relatives",
                at(today.minusDays(2), 10, 0), at(today.minusDays(2), 18, 0),
                at(today.minusDays(2), 10, 5), at(today.minusDays(2), 20, 30),
                PassStatus.EXPIRED, true, null, now);
        // 1005 approved but the exit date is over and nobody used it
        PASS_DAO.insertDemoPass(conn, "CS24002", "Cousin's engagement",
                at(today.minusDays(3), 9, 0), at(today.minusDays(3), 19, 0),
                null, null, PassStatus.APPROVED, false, null, now);
        // 1006 rejected
        PASS_DAO.insertDemoPass(conn, "CS24003", "Movie with friends",
                at(today.plusDays(2), 18, 0), at(today.plusDays(2), 23, 0),
                null, null, PassStatus.REJECTED, false, "Exams are near; please apply for a weekend.", now);
        // 1007 returned on time
        PASS_DAO.insertDemoPass(conn, "CS24001", "Bank work",
                at(today.minusDays(4), 9, 0), at(today.minusDays(4), 17, 0),
                at(today.minusDays(4), 9, 10), at(today.minusDays(4), 16, 40),
                PassStatus.EXPIRED, false, null, now);
    }

    private static LocalDateTime at(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute));
    }
}
