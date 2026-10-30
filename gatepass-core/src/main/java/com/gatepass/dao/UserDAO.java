package com.gatepass.dao;

import com.gatepass.db.DBConnection;
import com.gatepass.model.Parent;
import com.gatepass.model.Role;
import com.gatepass.model.SecurityGuard;
import com.gatepass.model.Student;
import com.gatepass.model.User;
import com.gatepass.model.Warden;
import com.gatepass.util.PasswordUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Optional;

/**
 * DAO (Data Access Object) for users, students and parents. All SQL about people lives here.
 *
 * <p>The password hash is read in exactly one place ({@link #authenticate}); no other query
 * selects that column, so a hash can never travel to a screen.</p>
 */
public class UserDAO {

    private static final String STUDENT_SELECT = """
            SELECT s.college_id, u.name, u.email, s.class_name, s.semester, s.hostel_roll,
                   p.parent_id, p.name AS parent_name, p.email AS parent_email, p.phone AS parent_phone
            FROM students s
            JOIN users u ON u.user_id = s.user_id
            JOIN parents p ON p.parent_id = s.parent_id
            """;
    private static final String BY_COLLEGE_ID = " WHERE s.college_id = ?";
    private static final String BY_USER_ID = " WHERE s.user_id = ?";

    // ---------------------------------------------------------------- login

    /**
     * Checks a login ID and password.
     *
     * @return the matching {@link Student}, {@link Warden} or {@link SecurityGuard};
     *         {@code null} when the ID is unknown or the password is wrong (the caller must
     *         not say which of the two it was)
     */
    public User authenticate(String loginId, String password) {
        if (loginId == null || password == null || loginId.isBlank() || password.isEmpty()) {
            return null;
        }
        String id = loginId.trim().toUpperCase(Locale.ROOT);
        String sql = "SELECT user_id, name, email, password_hash, role FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !PasswordUtil.verify(password, rs.getString("password_hash"))) {
                    return null;
                }
                String userId = rs.getString("user_id");
                String name = rs.getString("name");
                String email = rs.getString("email");
                return switch (Role.valueOf(rs.getString("role"))) {
                    case STUDENT -> queryStudent(conn, BY_USER_ID, userId);
                    case WARDEN -> new Warden(userId, name, email);
                    case GUARD -> new SecurityGuard(userId, name, email);
                };
            }
        } catch (SQLException e) {
            throw DataAccessException.from("checking the login", e);
        }
    }

    // ---------------------------------------------------------------- students / parents

    /** Full student profile including parent details; empty when the college ID does not exist. */
    public Optional<Student> findStudent(String collegeId) {
        if (collegeId == null || collegeId.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = DBConnection.getConnection()) {
            return Optional.ofNullable(
                    queryStudent(conn, BY_COLLEGE_ID, collegeId.trim().toUpperCase(Locale.ROOT)));
        } catch (SQLException e) {
            throw DataAccessException.from("loading a student", e);
        }
    }

    /** The parent registered for a student (used by the email feature). */
    public Optional<Parent> findParentByCollegeId(String collegeId) {
        return findStudent(collegeId).map(Student::getParent);
    }

    private Student queryStudent(Connection conn, String whereClause, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(STUDENT_SELECT + whereClause)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Parent parent = new Parent(rs.getInt("parent_id"), rs.getString("parent_name"),
                        rs.getString("parent_email"), rs.getString("parent_phone"));
                return new Student(rs.getString("college_id"), rs.getString("name"),
                        rs.getString("email"), rs.getString("class_name"), rs.getInt("semester"),
                        rs.getString("hostel_roll"), parent);
            }
        }
    }

    /**
     * Changes the email address of a student's parent (demo setup: point it at an inbox the team
     * can open). Returns false when the college ID does not exist.
     */
    public boolean updateParentEmail(String collegeId, String newEmail) {
        String sql = "UPDATE parents SET email = ? "
                + "WHERE parent_id = (SELECT parent_id FROM students WHERE college_id = ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newEmail.trim());
            ps.setString(2, collegeId.trim().toUpperCase(Locale.ROOT));
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw DataAccessException.from("updating the parent email", e);
        }
    }

    // ---------------------------------------------------------------- used by DatabaseInitializer

    /** True when the users table already has at least one row (so seeding must be skipped). */
    public boolean anyUserExists(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /** Inserts a parent and returns the generated parent_id. */
    public int insertParent(Connection conn, String name, String email, String phone) throws SQLException {
        String sql = "INSERT INTO parents (name, email, phone) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void insertUser(Connection conn, String userId, String name, String email,
                           String passwordHash, Role role) throws SQLException {
        String sql = "INSERT INTO users (user_id, name, email, password_hash, role) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, name);
            ps.setString(3, email);
            ps.setString(4, passwordHash);
            ps.setString(5, role.name());
            ps.executeUpdate();
        }
    }

    public void insertStudent(Connection conn, String collegeId, String userId, String className,
                              int semester, String hostelRoll, int parentId) throws SQLException {
        String sql = "INSERT INTO students (college_id, user_id, class_name, semester, hostel_roll, parent_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, collegeId);
            ps.setString(2, userId);
            ps.setString(3, className);
            ps.setInt(4, semester);
            ps.setString(5, hostelRoll);
            ps.setInt(6, parentId);
            ps.executeUpdate();
        }
    }
}
