package com.gatepass.guard;

import com.gatepass.model.PassStatus;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/** Colours the Status column and shows "LATE" in red in the movement table (POLYMORPHISM: overrides one method). */
class MovementCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setFont(table.getFont());
        if (!isSelected) {
            setForeground(table.getForeground());
        }
        if (value instanceof PassStatus status) {
            setFont(getFont().deriveFont(Font.BOLD));
            if (!isSelected) {
                setForeground(GuardStyle.statusColor(status));
            }
        } else if ("LATE".equals(value)) {
            setFont(getFont().deriveFont(Font.BOLD));
            if (!isSelected) {
                setForeground(GuardStyle.DANGER);
            }
        }
        return this;
    }
}
