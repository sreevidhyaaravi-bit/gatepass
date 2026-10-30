package com.gatepass.web;

import com.gatepass.db.DBConnection;
import com.gatepass.db.DatabaseInitializer;
import com.gatepass.service.AuthService;
import com.gatepass.service.PassService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Runs ONCE when Tomcat starts the website (before the first request).
 *
 * <ol>
 *   <li>Creates the tables and demo data if they do not exist yet (safe on every start).</li>
 *   <li>Creates ONE PassService and ONE AuthService and stores them in the ServletContext, so
 *       every servlet uses the same objects.</li>
 * </ol>
 *
 * <p>The website never records exits or entries (only the guard app does), so it never sends
 * parent emails. That is why its PassService uses the default notifier
 * ({@code MovementNotifier.NONE}) instead of an EmailService.</p>
 */
@WebListener
public class AppStartupListener implements ServletContextListener {

    static final String PASS_SERVICE = "passService";
    static final String AUTH_SERVICE = "authService";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        DatabaseInitializer.initialize();
        context.setAttribute(PASS_SERVICE, new PassService());
        context.setAttribute(AUTH_SERVICE, new AuthService());
        context.log("Digital Gate Pass website is ready. Database file: " + DBConnection.getDatabasePath());
    }
}
