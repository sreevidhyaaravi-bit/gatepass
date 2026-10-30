package com.gatepass.service;

import com.gatepass.dao.UserDAO;
import com.gatepass.model.Role;
import com.gatepass.model.User;
import java.util.Arrays;
import java.util.Optional;

/**
 * Login with a role check, shared by the website and the guard app.
 *
 * <p>The website allows STUDENT and WARDEN; the Swing app allows only GUARD. The caller says
 * which roles are acceptable, and a valid account of any other role is refused exactly like a
 * wrong password (so the screen never reveals which accounts exist).</p>
 */
public class AuthService {

    private final UserDAO userDAO;

    public AuthService() {
        this(new UserDAO());
    }

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public Optional<User> login(String loginId, String password, Role... allowedRoles) {
        User user = userDAO.authenticate(loginId, password);
        if (user == null || !Arrays.asList(allowedRoles).contains(user.getRole())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
