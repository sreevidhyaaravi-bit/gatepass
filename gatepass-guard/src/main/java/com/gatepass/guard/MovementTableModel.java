package com.gatepass.guard;

import com.gatepass.model.PassStatus;
import com.gatepass.service.GuardPassView;
import com.gatepass.util.DateTimeUtil;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Data behind the guard's JTable of gate movements (INHERITANCE: extends Swing's
 * AbstractTableModel and overrides the methods the JTable calls).
 *
 * <p>Rows are {@link GuardPassView} objects, so the table can only ever show the limited gate
 * information: no parent, email or reason.</p>
 */
class MovementTableModel extends AbstractTableModel {

    static final int STATUS_COLUMN = 4;
    static final int LATE_COLUMN = 9;

    private static final String[] COLUMNS = {
        "Pass no.", "Student", "College ID", "Hostel roll", "Status",
        "Requested exit", "Actual exit", "Expected return", "Actual entry", "Late"
    };

    private List<GuardPassView> rows = List.of();

    void setRows(List<GuardPassView> newRows) {
        rows = List.copyOf(newRows);
        fireTableDataChanged(); // tells the JTable to repaint
    }

    GuardPassView rowAt(int index) {
        return rows.get(index);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == STATUS_COLUMN ? PassStatus.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        GuardPassView v = rows.get(row);
        return switch (column) {
            case 0 -> v.passNo();
            case 1 -> v.studentName();
            case 2 -> v.collegeId();
            case 3 -> v.hostelRoll();
            case STATUS_COLUMN -> v.status();
            case 5 -> DateTimeUtil.display(v.exitDateTime());
            case 6 -> DateTimeUtil.display(v.actualExitDateTime());
            case 7 -> DateTimeUtil.display(v.expectedEntryDateTime());
            case 8 -> DateTimeUtil.display(v.actualEntryDateTime());
            case LATE_COLUMN -> v.lateFlag() ? "LATE" : "";
            default -> "";
        };
    }
}
