package com.gatepass.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gatepass.model.PassStatus;
import com.gatepass.service.GuardPassView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The guard's table shows exactly the gate columns, formatted for people. Runs without a screen. */
class MovementTableModelTest {

    @Test
    void columnsAndValues() {
        LocalDateTime exit = LocalDateTime.of(2026, 10, 29, 15, 0);
        GuardPassView late = new GuardPassView("GP1003", "Meera Joseph", "CS24003", "CSE", 3, "H24003",
                exit, exit.withHour(20), exit.withMinute(10), exit.plusDays(1).withHour(10).withMinute(0),
                PassStatus.EXPIRED, true);
        MovementTableModel model = new MovementTableModel();
        model.setRows(List.of(late));

        assertEquals(10, model.getColumnCount());
        assertEquals(1, model.getRowCount());
        assertEquals("GP1003", model.getValueAt(0, 0));
        assertEquals("Meera Joseph", model.getValueAt(0, 1));
        assertEquals(PassStatus.EXPIRED, model.getValueAt(0, MovementTableModel.STATUS_COLUMN));
        assertEquals("29 Oct 2026, 03:10 PM", model.getValueAt(0, 6));
        assertEquals("30 Oct 2026, 10:00 AM", model.getValueAt(0, 8));
        assertEquals("LATE", model.getValueAt(0, MovementTableModel.LATE_COLUMN));
        for (int c = 0; c < model.getColumnCount(); c++) {
            String name = model.getColumnName(c).toLowerCase();
            assertEquals(false, name.contains("parent") || name.contains("mail") || name.contains("reason"), name);
        }
    }
}
