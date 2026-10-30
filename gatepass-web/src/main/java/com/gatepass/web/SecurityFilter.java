package com.gatepass.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Runs before EVERY request (mapped to /* in web.xml, first in the chain).
 *
 * <ol>
 *   <li>Reads form data as UTF-8 (so names like "Méera" are stored correctly).</li>
 *   <li>Adds browser safety headers (no framing, no guessing of file types, scripts only from
 *       this website).</li>
 *   <li>CSRF check: every POST (except the login form) must carry the secret token that was
 *       put in the session at login. A form on another website cannot know it, so it cannot
 *       approve passes or submit requests using the warden's or student's login.</li>
 * </ol>
 */
public class SecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        request.setCharacterEncoding("UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; "
                + "img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'self'");

        if ("POST".equalsIgnoreCase(request.getMethod()) && !"/login".equals(request.getServletPath())) {
            HttpSession session = request.getSession(false);
            Object user = session == null ? null : session.getAttribute(BaseServlet.SESSION_USER);
            if (user == null) {
                // Typical cause: the page stayed open until the session timed out.
                response.sendRedirect(request.getContextPath() + "/login?msg=expired");
                return;
            }
            String expected = (String) session.getAttribute(BaseServlet.SESSION_CSRF);
            String received = request.getParameter("csrfToken");
            if (expected == null || received == null
                    || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    received.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form token");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
