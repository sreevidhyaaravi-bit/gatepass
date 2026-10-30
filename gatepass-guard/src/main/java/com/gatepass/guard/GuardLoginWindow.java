package com.gatepass.guard;

import com.gatepass.model.Role;
import com.gatepass.model.User;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * Login window of the guard app. Only accounts with the GUARD role can log in: student and
 * warden accounts are refused with the same message as a wrong password.
 */
public class GuardLoginWindow extends JFrame {

    private final GuardApp app;
    private final JTextField loginIdField = new JTextField(16);
    private final JPasswordField passwordField = new JPasswordField(16);
    private final JButton loginButton = new JButton("Log in");
    private boolean openingMainWindow;

    GuardLoginWindow(GuardApp app) {
        super("Gate Guard Login - Digital Gate Pass");
        this.app = app;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (!openingMainWindow) {
                    app.exit(); // closing the login window ends the program
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(GuardStyle.header("Digital Gate Pass", "Security guard console"), BorderLayout.NORTH);
        root.add(buildForm(), BorderLayout.CENTER);

        JLabel setup = new JLabel(app.setupSummary());
        setup.setFont(setup.getFont().deriveFont(11f));
        setup.setForeground(GuardStyle.MUTED);
        setup.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
        root.add(setup, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(loginButton); // Enter key = Log in
        loginButton.addActionListener(e -> attemptLogin());
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(24, 40, 20, 40));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Login ID"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(loginIdField, c);

        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Password"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(passwordField, c);

        c.gridx = 1;
        c.gridy = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        loginButton.setFont(loginButton.getFont().deriveFont(Font.BOLD));
        form.add(loginButton, c);

        JLabel note = new JLabel("Only security guard accounts can use this application.");
        note.setForeground(GuardStyle.MUTED);
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        form.add(note, c);
        return form;
    }

    /** Checks the password on a background thread (hashing takes a moment), then opens GuardWindow. */
    private void attemptLogin() {
        String loginId = loginIdField.getText();
        char[] password = passwordField.getPassword();
        if (loginId.isBlank() || password.length == 0) {
            JOptionPane.showMessageDialog(this, "Please enter your login ID and password.",
                    "Gate Guard", JOptionPane.WARNING_MESSAGE);
            return;
        }
        loginButton.setEnabled(false);
        new SwingWorker<Optional<User>, Void>() {
            @Override
            protected Optional<User> doInBackground() {
                try {
                    return app.authService().login(loginId, new String(password), Role.GUARD);
                } finally {
                    Arrays.fill(password, '\0'); // do not keep the password in memory
                }
            }

            @Override
            protected void done() { // back on the Swing event thread
                loginButton.setEnabled(true);
                try {
                    Optional<User> guard = get();
                    if (guard.isPresent()) {
                        openingMainWindow = true;
                        dispose();
                        app.showGuardWindow(guard.get());
                    } else {
                        passwordField.setText("");
                        JOptionPane.showMessageDialog(GuardLoginWindow.this,
                                "Login failed. Check the login ID and password.\n"
                                        + "Only security guard accounts can use this application.",
                                "Login failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    GuardStyle.showError(GuardLoginWindow.this, e);
                }
            }
        }.execute();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            loginIdField.requestFocusInWindow();
        }
    }
}
