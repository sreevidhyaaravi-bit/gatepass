package com.gatepass.guard;

import com.gatepass.dao.DataAccessException;
import com.gatepass.model.PassStatus;
import com.gatepass.service.BusinessRuleException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Colours and small helpers shared by the guard windows (same colours as the website). */
final class GuardStyle {

    static final Color BRAND = new Color(0x1E3A8A);
    static final Color MUTED = new Color(0x5B6475);
    static final Color OK = new Color(0x15803D);
    static final Color DANGER = new Color(0xB91C1C);
    static final Color WARN = new Color(0xB45309);

    private static final Logger LOG = Logger.getLogger(GuardStyle.class.getName());

    private GuardStyle() {
    }

    /** Dark blue title bar used at the top of both windows. */
    static JPanel header(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BRAND);
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(new Color(0xC7D2FE));
        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(titleLabel, BorderLayout.NORTH);
        text.add(subtitleLabel, BorderLayout.SOUTH);
        header.add(text, BorderLayout.WEST);
        return header;
    }

    /** Text colour for each status (also used in the movement table). */
    static Color statusColor(PassStatus status) {
        if (status == null) {
            return MUTED;
        }
        return switch (status) {
            case PENDING -> WARN;
            case APPROVED -> OK;
            case REJECTED -> DANGER;
            case EXITED -> new Color(0x1D4ED8);
            case EXPIRED -> new Color(0x475569);
        };
    }

    /** Light background for the big status label. */
    static Color statusBackground(PassStatus status) {
        if (status == null) {
            return new Color(0xEEF0F4);
        }
        return switch (status) {
            case PENDING -> new Color(0xFEF3C7);
            case APPROVED -> new Color(0xDCFCE7);
            case REJECTED -> new Color(0xFEE2E2);
            case EXITED -> new Color(0xDBEAFE);
            case EXPIRED -> new Color(0xEEF0F4);
        };
    }

    /**
     * Shows an error dialog. Messages from PassService and the DAO are written for users, so they
     * are shown as they are; anything unexpected gets a general message (details go to the log).
     */
    static void showError(Component parent, Throwable error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        String message;
        String title;
        if (cause instanceof BusinessRuleException) {
            message = cause.getMessage();
            title = "Not allowed";
        } else if (cause instanceof DataAccessException) {
            message = cause.getMessage();
            title = "Database problem";
        } else {
            LOG.log(Level.WARNING, "Unexpected error in the guard app", cause);
            message = "Something went wrong. Please try again.";
            title = "Error";
        }
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
