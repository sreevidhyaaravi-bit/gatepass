package com.gatepass.guard;

import com.gatepass.model.User;
import com.gatepass.service.EmailResult;
import com.gatepass.service.GuardPassView;
import com.gatepass.service.MovementResult;
import com.gatepass.util.AppConfig;
import com.gatepass.util.DateTimeUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Main window of the guard app: search a pass, check it, Mark Exit / Mark Entry, and see the
 * latest gate movements.
 *
 * <p>Rules: this window contains NONE. It only calls PassService (which checks everything again)
 * and shows the answer. Enabling or disabling the buttons is just a convenience for the guard.</p>
 *
 * <p>Threads: database work runs in a {@link SwingWorker} (a background thread) so the window never
 * freezes; results are shown in {@code done()}, which Swing runs on the event-dispatch thread.
 * Parent emails finish later; their result arrives through a CompletableFuture and is shown with
 * {@link SwingUtilities#invokeLater}.</p>
 */
public class GuardWindow extends JFrame {

    private final GuardApp app;
    private final User guard;

    private final JTextField passNoField = new JTextField(12);
    private final JButton searchButton = new JButton("Search");
    private final JButton markExitButton = new JButton("Mark Exit");
    private final JButton markEntryButton = new JButton("Mark Entry");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton logoutButton = new JButton("Log out");

    private final JLabel passNoValue = valueLabel();
    private final JLabel nameValue = valueLabel();
    private final JLabel collegeIdValue = valueLabel();
    private final JLabel hostelValue = valueLabel();
    private final JLabel classValue = valueLabel();
    private final JLabel requestedExitValue = valueLabel();
    private final JLabel expectedEntryValue = valueLabel();
    private final JLabel actualExitValue = valueLabel();
    private final JLabel actualEntryValue = valueLabel();
    private final JLabel lateValue = valueLabel();
    private final JLabel statusBadge = new JLabel("NO PASS SELECTED", SwingConstants.CENTER);

    private final JLabel movementResultLabel = new JLabel(" ");
    private final JLabel emailResultLabel = new JLabel(" ");
    private final JLabel tableInfoLabel = new JLabel(" ");

    private final MovementTableModel tableModel = new MovementTableModel();
    private final JTable table = new JTable(tableModel);

    private GuardPassView current;   // the pass shown in the details panel (null = none)
    private boolean busy;            // a search / exit / entry is running
    private boolean loggingOut;

    GuardWindow(GuardApp app, User guard) {
        super("Gate Guard - Digital Gate Pass");
        this.app = app;
        this.guard = guard;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (!loggingOut) {
                    app.exit();
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        body.add(buildSearchBar(), BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(12, 0));
        middle.add(buildDetailsPanel(), BorderLayout.CENTER);
        middle.add(buildActionsPanel(), BorderLayout.EAST);
        body.add(middle, BorderLayout.CENTER);
        body.add(buildTablePanel(), BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        passNoField.addActionListener(e -> search());   // Enter key in the field = Search
        searchButton.addActionListener(e -> search());
        markExitButton.addActionListener(e -> markExit());
        markEntryButton.addActionListener(e -> markEntry());
        refreshButton.addActionListener(e -> refresh());
        logoutButton.addActionListener(e -> logout());

        showPass(null);
        setSize(1180, 780);
        setMinimumSize(new Dimension(980, 680));
        setLocationRelativeTo(null);
        loadMovements();
    }

    // =========================================================== layout

    private JPanel buildHeader() {
        JPanel header = GuardStyle.header("Gate Guard Console",
                "Logged in as " + guard.getName() + " (" + guard.getUserId() + ")");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttons.setOpaque(false);
        buttons.add(refreshButton);
        buttons.add(logoutButton);
        header.add(buttons, BorderLayout.EAST);
        return header;
    }

    private JPanel buildSearchBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel label = new JLabel("Pass number");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        passNoField.setFont(passNoField.getFont().deriveFont(16f));
        searchButton.setFont(searchButton.getFont().deriveFont(Font.BOLD));
        JLabel hint = new JLabel("for example GP1002, then press Enter");
        hint.setForeground(GuardStyle.MUTED);
        bar.add(label);
        bar.add(passNoField);
        bar.add(searchButton);
        bar.add(hint);
        return bar;
    }

    private JPanel buildDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Pass details (gate information only)"),
                BorderFactory.createEmptyBorder(6, 10, 10, 10)));
        String[][] layout = {
            {"Pass number", "Status"},
            {"Student name", "College ID"},
            {"Hostel roll no.", "Class / semester"},
            {"Requested exit", "Expected return"},
            {"Actual exit", "Actual entry"},
            {"Late return", null},
        };
        JLabel[][] values = {
            {passNoValue, null},
            {nameValue, collegeIdValue},
            {hostelValue, classValue},
            {requestedExitValue, expectedEntryValue},
            {actualExitValue, actualEntryValue},
            {lateValue, null},
        };
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 4, 5, 14);
        for (int row = 0; row < layout.length; row++) {
            for (int pair = 0; pair < 2; pair++) {
                if (layout[row][pair] == null) {
                    continue;
                }
                c.gridy = row;
                c.gridx = pair * 2;
                c.weightx = 0;
                JLabel name = new JLabel(layout[row][pair]);
                name.setForeground(GuardStyle.MUTED);
                panel.add(name, c);
                c.gridx = pair * 2 + 1;
                c.weightx = 1;
                panel.add(row == 0 && pair == 1 ? statusBadge : values[row][pair], c);
            }
        }
        c.gridy = layout.length;   // empty last row takes the spare height, so the details sit at the top
        c.gridx = 0;
        c.weighty = 1;
        panel.add(new JLabel(), c);
        statusBadge.setOpaque(true);
        statusBadge.setFont(statusBadge.getFont().deriveFont(Font.BOLD, 13f));
        statusBadge.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        return panel;
    }

    private JPanel buildActionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Gate actions"),
                BorderFactory.createEmptyBorder(6, 10, 10, 10)));
        panel.setPreferredSize(new Dimension(320, 10));

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 10));
        for (JButton b : new JButton[] {markExitButton, markEntryButton}) {
            b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
            b.setPreferredSize(new Dimension(260, 48));
            buttons.add(b);
        }
        markExitButton.setToolTipText("Allowed only for an APPROVED pass on its exit date");
        markEntryButton.setToolTipText("Allowed only when the student has exited with this pass");

        JPanel results = new JPanel(new GridLayout(2, 1, 0, 6));
        results.setBorder(BorderFactory.createTitledBorder("Last operation"));
        results.add(movementResultLabel);
        results.add(emailResultLabel);

        panel.add(buttons, BorderLayout.NORTH);
        panel.add(results, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildTablePanel() {
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new MovementCellRenderer());
        table.setDefaultRenderer(String.class, new MovementCellRenderer());
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {70, 130, 85, 85, 85, 160, 160, 160, 160, 55}; // date columns need the most room
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewRow = table.getSelectedRow();
                if (e.getClickCount() == 2 && viewRow >= 0) { // double-click a row = open that pass
                    GuardPassView row = tableModel.rowAt(table.convertRowIndexToModel(viewRow));
                    passNoField.setText(row.passNo());
                    search();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(100, 260));
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        JLabel title = new JLabel("Gate movements (latest exit first) - double-click a row to open it");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        tableInfoLabel.setForeground(GuardStyle.MUTED);
        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.WEST);
        top.add(tableInfoLabel, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        return label;
    }

    // =========================================================== actions

    /** Search: PassService applies automatic expiry, then returns the gate view (or nothing). */
    private void search() {
        String passNo = passNoField.getText().trim();
        if (passNo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a pass number, for example GP1002.",
                    "Search", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runInBackground(() -> app.passService().searchPass(guard, passNo), found -> {
            showPass(found.orElse(null));
            loadMovements(); // a search can expire an old pass, so refresh the table too
            if (found.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Pass not found: " + passNo.toUpperCase(),
                        "Search", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    private void markExit() {
        if (current == null || !confirm("Record the EXIT of " + current.studentName()
                + " now?\nPass " + current.passNo(), "Mark Exit")) {
            return;
        }
        String passNo = current.passNo();
        runMovement(() -> app.passService().markExit(guard, passNo));
    }

    private void markEntry() {
        if (current == null || !confirm("Record the RETURN of " + current.studentName()
                + " now?\nPass " + current.passNo(), "Mark Entry")) {
            return;
        }
        String passNo = current.passNo();
        runMovement(() -> app.passService().markEntry(guard, passNo));
    }

    /**
     * Runs Mark Exit / Mark Entry. The database result is shown at once; the parent email result
     * is shown separately when it arrives (it never changes the saved movement).
     */
    private void runMovement(Supplier<MovementResult> action) {
        runInBackground(action, result -> {
            GuardPassView pass = result.pass();
            showPass(pass);
            loadMovements();
            setResult(movementResultLabel, pass.passNo() + ": " + result.message(), GuardStyle.OK);
            setResult(emailResultLabel, pass.passNo() + ": sending the parent email...", GuardStyle.MUTED);
            result.email().thenAccept(email -> SwingUtilities.invokeLater(() -> showEmailResult(pass.passNo(), email)));
            JOptionPane.showMessageDialog(this, result.message()
                            + "\n\nThe parent notification email is being sent in the background.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void showEmailResult(String passNo, EmailResult email) {
        setResult(emailResultLabel, passNo + ": " + email.message(), email.submitted() ? GuardStyle.OK : GuardStyle.WARN);
    }

    private void refresh() {
        loadMovements();
        if (current != null) {
            String passNo = current.passNo();
            runInBackground(() -> app.passService().searchPass(guard, passNo), found -> showPass(found.orElse(null)));
        }
    }

    private void logout() {
        loggingOut = true;
        dispose();
        app.showLogin();
    }

    /** Loads the movement table in the background (does not block the buttons). */
    private void loadMovements() {
        new SwingWorker<List<GuardPassView>, Void>() {
            @Override
            protected List<GuardPassView> doInBackground() {
                return app.passService().getGateMovements(guard);
            }

            @Override
            protected void done() {
                try {
                    tableModel.setRows(get());
                    tableInfoLabel.setText(tableModel.getRowCount() + " records, updated "
                            + DateTimeUtil.display(LocalDateTime.now(AppConfig.CAMPUS_ZONE)));
                } catch (InterruptedException | ExecutionException e) {
                    tableInfoLabel.setText("Could not load gate movements: "
                            + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                }
            }
        }.execute();
    }

    // =========================================================== helpers

    /** Shows a pass (or clears the panel) and enables only the buttons that make sense. */
    private void showPass(GuardPassView pass) {
        current = pass;
        if (pass == null) {
            for (JLabel l : new JLabel[] {passNoValue, nameValue, collegeIdValue, hostelValue, classValue,
                    requestedExitValue, expectedEntryValue, actualExitValue, actualEntryValue, lateValue}) {
                l.setText("-");
                l.setForeground(Color.DARK_GRAY);
            }
            statusBadge.setText("NO PASS SELECTED");
            statusBadge.setForeground(GuardStyle.MUTED);
            statusBadge.setBackground(GuardStyle.statusBackground(null));
        } else {
            passNoValue.setText(pass.passNo());
            nameValue.setText(pass.studentName());
            collegeIdValue.setText(pass.collegeId());
            hostelValue.setText(pass.hostelRoll());
            classValue.setText(pass.className() + " / semester " + pass.semester());
            requestedExitValue.setText(DateTimeUtil.display(pass.exitDateTime()));
            expectedEntryValue.setText(DateTimeUtil.display(pass.expectedEntryDateTime()));
            actualExitValue.setText(DateTimeUtil.display(pass.actualExitDateTime()));
            actualEntryValue.setText(DateTimeUtil.display(pass.actualEntryDateTime()));
            if (pass.lateFlag()) {
                lateValue.setText("YES - returned late");
                lateValue.setForeground(GuardStyle.DANGER);
            } else {
                lateValue.setText(pass.actualEntryDateTime() != null ? "No - returned on time" : "-");
                lateValue.setForeground(Color.DARK_GRAY);
            }
            statusBadge.setText(pass.status().name());
            statusBadge.setForeground(GuardStyle.statusColor(pass.status()));
            statusBadge.setBackground(GuardStyle.statusBackground(pass.status()));
        }
        updateButtons();
    }

    private void updateButtons() {
        searchButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
        markExitButton.setEnabled(!busy && current != null && current.canMarkExit());
        markEntryButton.setEnabled(!busy && current != null && current.canMarkEntry());
    }

    /**
     * Runs {@code task} on a background thread and gives its result to {@code onSuccess} on the
     * Swing thread. Errors (rule broken, database busy) are shown in a dialog, then the current
     * pass is reloaded so the screen shows the real state.
     */
    private <T> void runInBackground(Supplier<T> task, Consumer<T> onSuccess) {
        busy = true;
        updateButtons();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return task.get();
            }

            @Override
            protected void done() {
                busy = false;
                setCursor(Cursor.getDefaultCursor());
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    GuardStyle.showError(GuardWindow.this, e);
                    reloadCurrentPassQuietly();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                updateButtons();
            }
        }.execute();
    }

    private void reloadCurrentPassQuietly() {
        if (current == null) {
            return;
        }
        String passNo = current.passNo();
        new SwingWorker<Optional<GuardPassView>, Void>() {
            @Override
            protected Optional<GuardPassView> doInBackground() {
                return app.passService().searchPass(guard, passNo);
            }

            @Override
            protected void done() {
                try {
                    showPass(get().orElse(null));
                } catch (InterruptedException | ExecutionException ignored) {
                    // keep what is on screen
                }
            }
        }.execute();
    }

    private boolean confirm(String message, String title) {
        return JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static void setResult(JLabel label, String text, Color color) {
        // <html> lets long messages wrap inside the label
        label.setText("<html>" + escape(text) + "</html>");
        label.setForeground(color);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
