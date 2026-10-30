package com.gatepass.web;

import com.gatepass.dao.DataAccessException;
import com.gatepass.model.Role;
import com.gatepass.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Login page of the website, also shown at http://localhost:8080/ ("" = the site root).
 *
 * <p>Only STUDENT and WARDEN accounts may log in here. A guard account is refused exactly like a
 * wrong password, because guards use the Swing desktop app.</p>
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"", "/login"})
public class LoginServlet extends BaseServlet {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = currentUser(req);
        if (user != null) {
            redirect(dashboardPath(user), req, resp); // already logged in
            return;
        }
        showView("login", req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String loginId = req.getParameter("loginId");
        String password = req.getParameter("password");

        Optional<User> user;
        try {
            user = authService().login(loginId, password, Role.STUDENT, Role.WARDEN);
        } catch (DataAccessException e) {
            req.setAttribute("error", e.getMessage());
            req.setAttribute("loginId", loginId);
            showView("login", req, resp);
            return;
        }
        if (user.isEmpty()) {
            // Same message for "unknown ID", "wrong password" and "guard account": reveals nothing.
            req.setAttribute("error", "Invalid login ID or password.");
            req.setAttribute("loginId", loginId);
            showView("login", req, resp);
            return;
        }

        // New session on every login (prevents "session fixation" attacks).
        HttpSession old = req.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = req.getSession(true);
        session.setAttribute(SESSION_USER, user.get());
        session.setAttribute(SESSION_CSRF, newToken());
        redirect(dashboardPath(user.get()), req, resp);
    }

    /** 32 random bytes as text: the secret every form must send back (see SecurityFilter). */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
