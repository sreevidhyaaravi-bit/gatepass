package com.gatepass.dao;

import com.gatepass.db.DBConnection;
import com.gatepass.model.GatePass;
import com.gatepass.model.PassStatus;
import com.gatepass.model.Student;
import com.gatepass.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * DAO for the gate_passes table. All SQL about passes lives here; it contains NO business
 * rules (those are in PassService).
 *
 * <p>Every status/movement change is a CONDITIONAL UPDATE, e.g.
 * {@code UPDATE ... SET status='EXITED' WHERE pass_no=? AND status='APPROVED'}.
 * The method returns true only if a row really changed. If two guards press "Mark Exit" at the
 * same moment, the database lets exactly one UPDATE succeed; the other sees 0 rows changed.</p>
 *
 * <p>Passes are loaded together with the student's name, class, semester and hostel roll, but
 * NEVER with the student's email, parent or password. That keeps the guard's view small by
 * design.</p>
 */
public class GatePassDAO {

    private static final String SELECT_PASS = """
            SELECT g.pass_no, g.college_id, u.name AS student_name, s.class_name, s.semester,
                   s.hostel_roll, g.reason, g.exit_datetime, g.expected_entry_datetime,
                   g.actual_exit_datetime, g.actual_entry_datetime, g.status, g.late_flag,
                   g.rejection_remark, g.created_at
            FROM gate_passes g
            JOIN students s ON s.college_id = g.college_id
            JOIN users u ON u.user_id = s.user_id
            """;

    /** Newest pass first. Pass numbers grow with every new pass (GP1001, GP1002 ...). */
    private static final String NEWEST_FIRST = " ORDER BY CAST(SUBSTR(g.pass_no, 3) AS INTEGER) DESC";
    private static final String OLDEST_FIRST = " ORDER BY CAST(SUBSTR(g.pass_no, 3) AS INTEGER) ASC";

    // ---------------------------------------------------------------- create

    /**
     * Saves a new PENDING pass for the student and returns it with its generated pass number.
     * Runs in one transaction: the number is chosen and the row inserted under one write lock.
     */
    public GatePass create(Student student, String reason, LocalDateTime exitDateTime,
                           LocalDateTime expectedEntryDateTime, LocalDateTime createdAt) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String passNo = insertRow(conn, student.getCollegeId(), reason, exitDateTime,
                        expectedEntryDateTime, null, null, PassStatus.PENDING, false, null, createdAt);
                GatePass saved = findByPassNo(conn, passNo).orElseThrow(
                        () -> new IllegalStateException("Saved pass could not be read back"));
                conn.commit();
                return saved;
            } catch (SQLException | RuntimeException e) {
                conn.rollback(); // undo everything if any step failed
                throw e;
            }
        } catch (SQLException e) {
            throw DataAccessException.from("saving the gate pass", e);
        }
    }

    /**
     * Inserts a pass in ANY state. Only used to create demo data. Must be called inside the
     * caller's transaction.
     */
    public String insertDemoPass(Connection conn, String collegeId, String reason,
                                 LocalDateTime exitDateTime, LocalDateTime expectedEntryDateTime,
                                 LocalDateTime actualExit, LocalDateTime actualEntry,
                                 PassStatus status, boolean lateFlag, String remark,
                                 LocalDateTime createdAt) throws SQLException {
        return insertRow(conn, collegeId, reason, exitDateTime, expectedEntryDateTime,
                actualExit, actualEntry, status, lateFlag, remark, createdAt);
    }

    public void deleteAll(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM gate_passes");
        }
    }

    private String insertRow(Connection conn, String collegeId, String reason,
                             LocalDateTime exitDateTime, LocalDateTime expectedEntryDateTime,
                             LocalDateTime actualExit, LocalDateTime actualEntry,
                             PassStatus status, boolean lateFlag, String remark,
                             LocalDateTime createdAt) throws SQLException {
        String passNo = nextPassNo(conn);
        String sql = "INSERT INTO gate_passes (pass_no, college_id, reason, exit_datetime, "
                + "expected_entry_datetime, actual_exit_datetime, actual_entry_datetime, status, "
                + "late_flag, rejection_remark, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passNo);
            ps.setString(2, collegeId);
            ps.setString(3, reason);
            ps.setString(4, DateTimeUtil.format(exitDateTime));
            ps.setString(5, DateTimeUtil.format(expectedEntryDateTime));
            ps.setString(6, DateTimeUtil.format(actualExit));
            ps.setString(7, DateTimeUtil.format(actualEntry));
            ps.setString(8, status.name());
            ps.setInt(9, lateFlag ? 1 : 0);
            ps.setString(10, remark);
            ps.setString(11, DateTimeUtil.format(createdAt));
            ps.executeUpdate();
        }
        return passNo;
    }

    /** Next free number: GP1001 for the first pass, then GP1002, ... (inside the write lock). */
    private String nextPassNo(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTR(pass_no, 3) AS INTEGER)), 1000) + 1 FROM gate_passes";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return "GP" + rs.getInt(1);
        }
    }

    // ---------------------------------------------------------------- read

    public Optional<GatePass> findByPassNo(String passNo) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByPassNo(conn, passNo);
        } catch (SQLException e) {
            throw DataAccessException.from("finding a pass", e);
        }
    }

    private Optional<GatePass> findByPassNo(Connection conn, String passNo) throws SQLException {
        if (passNo == null || passNo.isBlank()) {
            return Optional.empty();
        }
        List<GatePass> found = query(conn, SELECT_PASS + " WHERE g.pass_no = ?",
                passNo.trim().toUpperCase(Locale.ROOT));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** Passes of one student, newest first. {@code limit <= 0} means "all of them". */
    public List<GatePass> findByStudent(String collegeId, int limit) {
        String sql = SELECT_PASS + " WHERE g.college_id = ?" + NEWEST_FIRST + " LIMIT ?";
        return list("loading pass history", sql, collegeId, limit <= 0 ? -1 : limit);
    }

    public List<GatePass> findByStatus(PassStatus status) {
        return list("loading passes", SELECT_PASS + " WHERE g.status = ?" + OLDEST_FIRST, status.name());
    }

    /**
     * Passes that have an actual exit recorded (so they are "gate movements"), latest exit first.
     * The ORDER BY on the stored text only sorts the screen; it is not a business comparison,
     * and the fixed zero-padded format sorts correctly.
     */
    public List<GatePass> findMovements(int limit) {
        String sql = SELECT_PASS + " WHERE g.actual_exit_datetime IS NOT NULL"
                + " ORDER BY g.actual_exit_datetime DESC, g.pass_no DESC LIMIT ?";
        return list("loading gate movements", sql, limit <= 0 ? -1 : limit);
    }

    /** Passes where the student came back after the expected time (late_flag = 1). */
    public List<GatePass> findLateReturns() {
        String sql = SELECT_PASS + " WHERE g.late_flag = 1"
                + " ORDER BY g.actual_entry_datetime DESC, g.pass_no DESC";
        return list("loading late returns", sql);
    }

    // ---------------------------------------------------------------- conditional updates

    /** PENDING -> APPROVED. Returns false if the pass was not PENDING any more. */
    public boolean approve(String passNo) {
        return update("approving the pass",
                "UPDATE gate_passes SET status = 'APPROVED' WHERE pass_no = ? AND status = 'PENDING'",
                passNo);
    }

    /** PENDING -> REJECTED with a remark. Returns false if the pass was not PENDING any more. */
    public boolean reject(String passNo, String remark) {
        return update("rejecting the pass",
                "UPDATE gate_passes SET status = 'REJECTED', rejection_remark = ? "
                        + "WHERE pass_no = ? AND status = 'PENDING'",
                remark, passNo);
    }

    /** APPROVED -> EXITED and stores the actual exit time. False if someone else got there first. */
    public boolean recordExit(String passNo, LocalDateTime actualExit) {
        return update("recording the exit",
                "UPDATE gate_passes SET status = 'EXITED', actual_exit_datetime = ? "
                        + "WHERE pass_no = ? AND status = 'APPROVED' AND actual_exit_datetime IS NULL",
                DateTimeUtil.format(actualExit), passNo);
    }

    /** EXITED -> EXPIRED, stores the actual entry time and the late flag. */
    public boolean recordEntry(String passNo, LocalDateTime actualEntry, boolean late) {
        return update("recording the entry",
                "UPDATE gate_passes SET status = 'EXPIRED', actual_entry_datetime = ?, late_flag = ? "
                        + "WHERE pass_no = ? AND status = 'EXITED' AND actual_entry_datetime IS NULL",
                DateTimeUtil.format(actualEntry), late ? 1 : 0, passNo);
    }

    /** APPROVED (never used) -> EXPIRED. An EXITED pass is never touched by this. */
    public boolean expireUnused(String passNo) {
        return update("expiring an unused pass",
                "UPDATE gate_passes SET status = 'EXPIRED' "
                        + "WHERE pass_no = ? AND status = 'APPROVED' AND actual_exit_datetime IS NULL",
                passNo);
    }

    // ---------------------------------------------------------------- helpers

    private boolean update(String action, String sql, Object... params) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate() == 1; // exactly one row changed = the condition was still true
        } catch (SQLException e) {
            throw DataAccessException.from(action, e);
        }
    }

    private List<GatePass> list(String action, String sql, Object... params) {
        try (Connection conn = DBConnection.getConnection()) {
            return query(conn, sql, params);
        } catch (SQLException e) {
            throw DataAccessException.from(action, e);
        }
    }

    private List<GatePass> query(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<GatePass> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    /** Turns one database row into a GatePass object (text -> LocalDateTime happens here). */
    private GatePass map(ResultSet rs) throws SQLException {
        Student student = new Student(rs.getString("college_id"), rs.getString("student_name"),
                rs.getString("class_name"), rs.getInt("semester"), rs.getString("hostel_roll"));
        return new GatePass(
                rs.getString("pass_no"),
                student,
                rs.getString("reason"),
                DateTimeUtil.parseDb(rs.getString("exit_datetime")),
                DateTimeUtil.parseDb(rs.getString("expected_entry_datetime")),
                DateTimeUtil.parseDb(rs.getString("actual_exit_datetime")),
                DateTimeUtil.parseDb(rs.getString("actual_entry_datetime")),
                PassStatus.valueOf(rs.getString("status")),
                rs.getInt("late_flag") == 1,
                rs.getString("rejection_remark"),
                DateTimeUtil.parseDb(rs.getString("created_at")));
    }
}
