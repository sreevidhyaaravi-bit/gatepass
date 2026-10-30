package com.gatepass.guard;

import com.gatepass.db.DBConnection;
import com.gatepass.db.DatabaseInitializer;
import com.gatepass.email.EmailService;
import com.gatepass.model.User;
import com.gatepass.service.AuthService;
import com.gatepass.service.PassService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The security guard's desktop program: its OWN main(), separate from the website.
 *
 * <p>It builds the same shared objects the website uses (PassService, AuthService) on the same
 * database file, plus a real {@link EmailService}, because only the guard records exits and
 * entries, and those trigger the parent emails.</p>
 *
 * <p>Swing request flow:  GuardWindow button -> PassService -> GatePassDAO -> SQLite.</p>
 */
public final class GuardApp {

    private static final Logger LOG = Logger.getLogger(GuardApp.class.getName());

    private final PassService passService;
    private final AuthService authService;
    private final EmailService emailService;

    GuardApp(PassService passService, AuthService authService, EmailService emailService) {
        this.passService = passService;
        this.authService = authService;
        this.emailService = emailService;
    }

    public static void main(String[] args) {
        useSystemLookAndFeel();
        try {
            DatabaseInitializer.initialize(); // creates tables + demo data if this is the first start
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Database could not be opened", e);
            JOptionPane.showMessageDialog(null, "The gate pass database could not be opened:\n"
                    + DBConnection.getDatabasePath() + "\n\n" + e.getMessage(),
                    "Gate Guard", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        EmailService email = EmailService.fromSettingsFile();
        LOG.info(email.describeStatus());
        GuardApp app = new GuardApp(new PassService(email), new AuthService(), email);
        SwingUtilities.invokeLater(app::showLogin); // Swing windows are created on the event thread
    }

    PassService passService() {
        return passService;
    }

    AuthService authService() {
        return authService;
    }

    /** Status line for the login window: database file and whether parent emails are on. */
    String setupSummary() {
        return "Database: " + DBConnection.getDatabasePath() + "   |   " + emailService.describeStatus();
    }

    void showLogin() {
        new GuardLoginWindow(this).setVisible(true);
    }

    void showGuardWindow(User guard) {
        new GuardWindow(this, guard).setVisible(true);
    }

    /** Closing the program: let emails that are already being sent finish (max 10 s), then exit. */
    void exit() {
        Thread closer = new Thread(() -> {
            emailService.shutdown(10);
            System.exit(0);
        }, "gate-guard-exit");
        closer.start();
    }

    private static void useSystemLookAndFeel() {
        try {
            // Windows look on Windows, Mac look on macOS
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // the default Java look is fine too
        }
    }
}
