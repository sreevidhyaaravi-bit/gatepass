package com.gatepass.web;

import com.gatepass.model.Role;
import com.gatepass.model.User;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Lets a request through only when someone is logged in WITH THE RIGHT ROLE.
 *
 * <p>One class, used twice in web.xml with a different {@code role} setting:</p>
 * <ul>
 *   <li>{@code /student/*}: role STUDENT (a warden cannot open the student pages),</li>
 *   <li>{@code /warden/*}: role WARDEN (a student cannot open warden pages or approve passes).</li>
 * </ul>
 * <p>PassService checks the role again for every action, so even a missed URL would be refused.</p>
 */
public class RoleFilter implements Filter {

    private Role requiredRole;

    @Override
    public void init(FilterConfig config) {
        requiredRole = Role.valueOf(config.getInitParameter("role"));
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        User user = BaseServlet.currentUser(request);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login?msg=login");
            return;
        }
        if (user.getRole() != requiredRole) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        // Personal data: the browser must not keep a copy (Back after logout shows nothing).
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        chain.doFilter(request, response);
    }
}
