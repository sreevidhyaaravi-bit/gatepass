package com.gatepass.web;

import com.gatepass.dao.DataAccessException;
import com.gatepass.model.User;
import com.gatepass.service.AuthService;
import com.gatepass.service.BusinessRuleException;
import com.gatepass.service.PassService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parent class of every servlet in the website (INHERITANCE). It holds the small helpers all
 * servlets need, so each servlet only contains its own steps:
 * <ul>
 *   <li>getting the shared PassService / AuthService,</li>
 *   <li>reading the logged-in user from the session,</li>
 *   <li>showing a JSP view, redirecting, and "flash" messages that survive one redirect,</li>
 *   <li>turning database and rule errors into a friendly error page (never a stack trace).</li>
 * </ul>
 * Servlets contain NO business rules: they read the form, call PassService, and show the result.
 */
public abstract class BaseServlet extends HttpServlet {

    /** Session attribute holding the logged-in {@link User}. */
    static final String SESSION_USER = "user";
    /** Session attribute holding the CSRF token that every POST form must send back. */
    static final String SESSION_CSRF = "csrfToken";
    private static final String SESSION_FLASH = "flash";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            super.service(req, resp); // calls doGet / doPost of the subclass
        } catch (DataAccessException | BusinessRuleException e) {
            // These messages are written for users (no SQL, no file paths), so they can be shown.
            if (resp.isCommitted()) {
                throw e;
            }
            req.setAttribute("errorMessage", e.getMessage());
            resp.setStatus(e instanceof DataAccessException
                    ? HttpServletResponse.SC_SERVICE_UNAVAILABLE : HttpServletResponse.SC_BAD_REQUEST);
            req.getRequestDispatcher("/WEB-INF/jsp/error.jsp").forward(req, resp);
        }
    }

    protected PassService passService() {
        return (PassService) getServletContext().getAttribute(AppStartupListener.PASS_SERVICE);
    }

    protected AuthService authService() {
        return (AuthService) getServletContext().getAttribute(AppStartupListener.AUTH_SERVICE);
    }

    /** The logged-in user, or null. (The role filters guarantee it is set on /student and /warden pages.) */
    protected static User currentUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (User) session.getAttribute(SESSION_USER);
    }

    /** Where each role lands after login: /student/dashboard or /warden/dashboard. */
    protected static String dashboardPath(User user) {
        return "/" + user.getRole().name().toLowerCase(Locale.ROOT) + "/dashboard";
    }

    /** Shows /WEB-INF/jsp/{view}.jsp. JSPs inside WEB-INF cannot be opened directly by a browser. */
    protected void showView(String view, HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        moveFlashToRequest(req);
        req.getRequestDispatcher("/WEB-INF/jsp/" + view + ".jsp").forward(req, resp);
    }

    protected void redirect(String path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendRedirect(req.getContextPath() + path);
    }

    /**
     * Saves a value for the NEXT page only (Post/Redirect/Get pattern: after a form is submitted
     * we redirect, so pressing F5 never submits the form twice).
     */
    @SuppressWarnings("unchecked")
    protected static void flash(HttpServletRequest req, String key, String value) {
        HttpSession session = req.getSession();
        Map<String, String> flash = (Map<String, String>) session.getAttribute(SESSION_FLASH);
        if (flash == null) {
            flash = new HashMap<>();
            session.setAttribute(SESSION_FLASH, flash);
        }
        flash.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private static void moveFlashToRequest(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return;
        }
        Map<String, String> flash = (Map<String, String>) session.getAttribute(SESSION_FLASH);
        if (flash != null) {
            flash.forEach(req::setAttribute);
            session.removeAttribute(SESSION_FLASH);
        }
    }
}
