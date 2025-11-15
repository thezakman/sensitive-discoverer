package com.tzm.supafinder.utils;

import com.tzm.supafinder.model.LogEntity;
import com.tzm.supafinder.model.LogEntriesManager;
import com.tzm.supafinder.ui.table.LogsTableModel;

import javax.swing.SwingUtilities;
import java.util.function.Consumer;

public class LoggerUtils {
    public static Consumer<LogEntity> createAddLogEntryCallback(LogEntriesManager logEntries,
                                                                Object logEntriesLock,
                                                                LogsTableModel logsTableModel) {
        return (LogEntity logEntry) -> SwingUtilities.invokeLater(() -> {
            synchronized (logEntriesLock) {
                if (!logEntries.contains(logEntry)) {
                    logEntries.add(logEntry);
                    System.out.println("[DEBUG] Added log entry. Total entries: " + logEntries.size());

                    if (logsTableModel == null) {
                        System.out.println("[DEBUG] logsTableModel is null!");
                        return;
                    }

                    // Use fireTableDataChanged instead of fireTableRowsInserted to avoid
                    // IndexOutOfBoundsException with RowSorter in concurrent scenarios
                    logsTableModel.fireTableDataChanged();
                    System.out.println("[DEBUG] Fired table data changed");
                } else {
                    System.out.println("[DEBUG] Duplicate entry detected, skipping");
                }
            }
        });
    }
}
