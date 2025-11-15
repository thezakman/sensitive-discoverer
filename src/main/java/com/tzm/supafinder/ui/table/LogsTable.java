package com.tzm.supafinder.ui.table;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.tzm.supafinder.model.LogEntity;
import com.tzm.supafinder.model.LogEntriesManager;
import com.tzm.supafinder.utils.ImportanceColorScheme;
import com.tzm.supafinder.utils.UIConstants;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

/**
 * JTable for Viewing Logs
 */
public class LogsTable extends JTable {

    private final LogEntriesManager logEntries;
    private final HttpRequestEditor requestViewer;
    private final HttpResponseEditor responseViewer;

    public LogsTable(LogsTableModel logsTableModel, LogEntriesManager logEntries, HttpRequestEditor requestViewer, HttpResponseEditor responseViewer) {
        super(logsTableModel);

        this.setAutoCreateRowSorter(false);
        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.logEntries = logEntries;
        this.requestViewer = requestViewer;
        this.responseViewer = responseViewer;

        // Set custom renderer for Importance column (index 1 after reordering)
        setupImportanceColumnRenderer();

        // Set optimal column widths
        setupColumnWidths();
    }

    private void setupImportanceColumnRenderer() {
        // Get Importance column index from LogsTableModel.Column
        int importanceColumnIndex = LogsTableModel.Column.IMPORTANCE.getIndex();

        this.getColumnModel().getColumn(importanceColumnIndex).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    int modelRow = convertRowIndexToModel(row);
                    LogEntity logEntity = logEntries.get(modelRow);
                    if (logEntity != null) {
                        int importance = logEntity.getRegexEntity().getImportance();
                        Color bgColor = ImportanceColorScheme.getColor(importance);
                        c.setBackground(bgColor);
                        c.setForeground(Color.BLACK);
                    }
                } else {
                    // Keep default selection colors
                    c.setBackground(table.getSelectionBackground());
                    c.setForeground(table.getSelectionForeground());
                }

                return c;
            }
        });
    }

    private void setupColumnWidths() {
        // Set optimal column widths based on LogsTableModel.Column order
        // After reordering: IMPORTANCE, REGEX, MATCH, URL, SECTION

        int importanceIdx = LogsTableModel.Column.IMPORTANCE.getIndex();
        this.getColumnModel().getColumn(importanceIdx).setMinWidth(UIConstants.COL_WIDTH_IMPORTANCE_MIN);
        this.getColumnModel().getColumn(importanceIdx).setMaxWidth(UIConstants.COL_WIDTH_IMPORTANCE_MAX);
        this.getColumnModel().getColumn(importanceIdx).setPreferredWidth(UIConstants.COL_WIDTH_IMPORTANCE_PREF);

        int regexIdx = LogsTableModel.Column.REGEX.getIndex();
        this.getColumnModel().getColumn(regexIdx).setMinWidth(UIConstants.COL_WIDTH_REGEX_MIN);
        this.getColumnModel().getColumn(regexIdx).setPreferredWidth(UIConstants.COL_WIDTH_REGEX_PREF);

        int matchIdx = LogsTableModel.Column.MATCH.getIndex();
        this.getColumnModel().getColumn(matchIdx).setMinWidth(UIConstants.COL_WIDTH_MATCH_MIN);
        this.getColumnModel().getColumn(matchIdx).setPreferredWidth(UIConstants.COL_WIDTH_MATCH_PREF);

        int urlIdx = LogsTableModel.Column.URL.getIndex();
        this.getColumnModel().getColumn(urlIdx).setMinWidth(UIConstants.COL_WIDTH_URL_MIN);
        this.getColumnModel().getColumn(urlIdx).setPreferredWidth(UIConstants.COL_WIDTH_URL_PREF);

        int sectionIdx = LogsTableModel.Column.SECTION.getIndex();
        this.getColumnModel().getColumn(sectionIdx).setMinWidth(UIConstants.COL_WIDTH_SECTION_MIN);
        this.getColumnModel().getColumn(sectionIdx).setMaxWidth(UIConstants.COL_WIDTH_SECTION_MAX);
        this.getColumnModel().getColumn(sectionIdx).setPreferredWidth(UIConstants.COL_WIDTH_SECTION_PREF);
    }

    @Override
    public void changeSelection(int row, int col, boolean toggle, boolean extend) {
        super.changeSelection(row, col, toggle, extend);
        int realRow = this.convertRowIndexToModel(row);
        LogEntity logEntry = logEntries.get(realRow);

        // Null check to prevent crashes
        if (logEntry != null) {
            updateRequestViewers(logEntry.getRequest(), logEntry.getResponse(), logEntry.getMatch());
        }
    }

    public void updateRequestViewers(HttpRequest request, HttpResponse response, String search) {
        SwingUtilities.invokeLater(() -> {
            requestViewer.setRequest(request);
            requestViewer.setSearchExpression(search);
            responseViewer.setResponse(response);
            responseViewer.setSearchExpression(search);
        });
    }
}
