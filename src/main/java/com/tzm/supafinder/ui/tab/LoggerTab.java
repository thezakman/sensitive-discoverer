package com.tzm.supafinder.ui.tab;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.tzm.supafinder.MainUI;
import com.tzm.supafinder.RegexScanner;
import com.tzm.supafinder.model.LogEntity;
import com.tzm.supafinder.model.LogEntriesManager;
import com.tzm.supafinder.ui.LogsTableContextMenu;
import com.tzm.supafinder.ui.PopupMenuButton;
import com.tzm.supafinder.ui.table.LogsTable;
import com.tzm.supafinder.ui.table.LogsTableModel;
import com.tzm.supafinder.utils.FileUtils;
import com.tzm.supafinder.utils.LoggerUtils;
import com.tzm.supafinder.utils.SwingUtils;
import com.tzm.supafinder.utils.ImportanceUtils;
import com.tzm.supafinder.utils.ImportanceColorScheme;
import com.tzm.supafinder.utils.UIConstants;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.tzm.supafinder.utils.Messages.getLocaleString;
import static com.tzm.supafinder.utils.Utils.createGsonBuilder;

public class LoggerTab implements ApplicationTab {
    private static final String TAB_NAME = getLocaleString("tab-logger");

    private final MainUI mainUI;
    private final JPanel panel;
    /**
     * Manager for the list containing the findings history (log entries).
     * <br><br>
     * When running multiple analysis on the same RegexScanner instance,
     * this list remains the same unless manually cleared.
     * This is required for not logging the same finding twice.
     */
    private final LogEntriesManager logEntriesManager;
    private final Object loggerLock = new Object();
    private final RegexScanner regexScanner;
    private HttpRequestEditor originalRequestViewer;
    private HttpResponseEditor originalResponseViewer;
    private LogsTableModel logsTableModel;
    private LogsTable logsTable;
    private TableRowSorter<LogsTableModel> logsTableRowSorter;
    private boolean isAnalysisRunning;
    private Thread analyzeProxyHistoryThread;
    // Filter state for re-applying filters when importance changes
    private JTextField searchField;
    private JCheckBox regexCheckbox;
    private JCheckBox matchCheckbox;
    private JCheckBox URLCheckbox;
    private JCheckBox UniqueCheckbox;

    public LoggerTab(MainUI mainUI) {
        this.mainUI = mainUI;
        this.isAnalysisRunning = false;
        this.analyzeProxyHistoryThread = null;
        this.logEntriesManager = new LogEntriesManager();
        this.regexScanner = new RegexScanner(
                this.mainUI.getBurpApi(),
                this.mainUI.getScannerOptions());

        // Inject log entries manager for real-time analysis
        this.regexScanner.setLogEntriesManager(this.logEntriesManager);

        // keep as last call
        this.panel = this.createPanel();

        // Register listener for importance changes
        this.mainUI.getScannerOptions().addImportanceChangeListener(this::refreshFilterForImportance);
    }

    private JPanel createPanel() {
        JPanel box;
        JPanel boxCenter;
        JPanel boxHeader;
        JScrollPane logEntriesPane;

        logEntriesPane = createLogEntriesTable();

        box = new JPanel();
        box.setLayout(new BorderLayout(0, 0));
        boxHeader = createHeaderBox(logEntriesPane);
        box.add(boxHeader, BorderLayout.NORTH);
        boxCenter = createCenterBox(logEntriesPane);
        box.add(boxCenter, BorderLayout.CENTER);

        // Add statistics panel at bottom
        JPanel statsPanel = createStatsPanel();
        box.add(statsPanel, BorderLayout.SOUTH);

        return box;
    }

    /**
     * Function to call before an analysis start.
     * It performs operations required before an analysis.
     */
    private void preAnalysisOperations() {
        // disable components that shouldn't be used while scanning
        SwingUtilities.invokeLater(() -> SwingUtils.setEnabledRecursiveComponentsWithProperty(this.mainUI.getMainPanel(), false, "analysisDependent"));
        // save current scan options
        this.mainUI.getScannerOptions().saveToPersistentStorage();
    }

    /**
     * Function to call after an analysis start.
     * It performs operations required after an analysis.
     */
    private void postAnalysisOperations() {
        // re-enable components not usable while scanning
        SwingUtilities.invokeLater(() -> SwingUtils.setEnabledRecursiveComponentsWithProperty(this.mainUI.getMainPanel(), true, "analysisDependent"));
    }

    private JPanel createCenterBox(JScrollPane logEntriesPane) {
        JPanel boxCenter;
        JPanel responsePanel;
        JPanel requestPanelHeader;
        JPanel requestPanel;
        JPanel responsePanelHeader;
        GridBagConstraints gbc;

        boxCenter = new JPanel();
        boxCenter.setLayout(new GridBagLayout());

        // vertical split plane - log entries on top and req/res editor on bottom
        JSplitPane verticalSplitPane = new JSplitPane();
        verticalSplitPane.setOrientation(0);
        verticalSplitPane.setResizeWeight(0.6);
        gbc = createGridConstraints(0, 0, 1.0, 1.0, GridBagConstraints.BOTH);
        boxCenter.add(verticalSplitPane, gbc);
        verticalSplitPane.setLeftComponent(logEntriesPane);
        JSplitPane requestResponseSplitPane = new JSplitPane();
        requestResponseSplitPane.setPreferredSize(new Dimension(233, 150));
        requestResponseSplitPane.setResizeWeight(0.5);
        verticalSplitPane.setRightComponent(requestResponseSplitPane);

        // request panel
        requestPanel = new JPanel(new BorderLayout(0, 0));
        requestResponseSplitPane.setLeftComponent(requestPanel);
        requestPanelHeader = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        requestPanel.add(requestPanelHeader, BorderLayout.NORTH);
        final JLabel requestLabel = new JLabel(getLocaleString("common-request"));
        requestLabel.setFont(MainUI.UIOptions.H2_FONT);
        requestLabel.setForeground(MainUI.UIOptions.ACCENT_COLOR);
        requestPanelHeader.add(requestLabel, BorderLayout.NORTH);
        requestPanel.add(this.originalRequestViewer.uiComponent(), BorderLayout.CENTER);
        responsePanel = new JPanel(new BorderLayout(0, 0));
        requestResponseSplitPane.setRightComponent(responsePanel);

        // response panel
        responsePanelHeader = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        responsePanel.add(responsePanelHeader, BorderLayout.NORTH);
        final JLabel responseLabel = new JLabel(getLocaleString("common-response"));
        responseLabel.setFont(MainUI.UIOptions.H2_FONT);
        responseLabel.setForeground(MainUI.UIOptions.ACCENT_COLOR);
        responsePanelHeader.add(responseLabel, BorderLayout.NORTH);
        responsePanel.add(this.originalResponseViewer.uiComponent(), BorderLayout.CENTER);

        return boxCenter;
    }

    private JPanel createHeaderBox(JScrollPane logEntriesPane) {
        JPanel headerBox;
        JPanel analysisBar;
        JPanel resultsFilterBar;
        JPanel toolSourceFilterPanel;
        JPanel originFilterPanel;
        JPanel filtersContainer;
        GridBagConstraints gbc;

        headerBox = new JPanel();
        headerBox.setLayout(new GridBagLayout());

        analysisBar = createAnalysisBar(logEntriesPane);
        gbc = createGridConstraints(0, 0, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
        headerBox.add(analysisBar, gbc);

        // Container for tool source and origin filters
        filtersContainer = new JPanel();
        filtersContainer.setLayout(new GridBagLayout());

        toolSourceFilterPanel = createToolSourceFilterPanel();
        gbc = createGridConstraints(0, 0, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(5, 10, 2, 10);
        filtersContainer.add(toolSourceFilterPanel, gbc);

        originFilterPanel = createOriginFilterPanel();
        gbc = createGridConstraints(0, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(2, 10, 5, 10);
        filtersContainer.add(originFilterPanel, gbc);

        gbc = createGridConstraints(0, 1, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
        headerBox.add(filtersContainer, gbc);

        resultsFilterBar = createResultsFilterBar();
        gbc = createGridConstraints(0, 2, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(2, 10, 5, 10);
        headerBox.add(resultsFilterBar, gbc);

        return headerBox;
    }

    private JPanel createResultsFilterBar() {
        JPanel resultsFilterBar;
        GridBagConstraints gbc;

        resultsFilterBar = new JPanel();
        resultsFilterBar.setLayout(new GridBagLayout());

        JLabel resultsCountLabel = new JLabel(getLocaleString("logger-resultsCount-label"));
        gbc = createGridConstraints(0, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(0, 0, 0, 5);
        resultsFilterBar.add(resultsCountLabel, gbc);
        JLabel filteredCountValueLabel = new JLabel("0");
        gbc = createGridConstraints(1, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        logsTableRowSorter.addRowSorterListener(rowSorterEvent -> SwingUtilities.invokeLater(() -> {
            filteredCountValueLabel.setText(String.valueOf(logsTable.getRowSorter().getViewRowCount()));
        }));
        resultsFilterBar.add(filteredCountValueLabel, gbc);
        JLabel totalCountValueLabel = new JLabel("/0");
        logEntriesManager.subscribeChangeListener(entriesCount -> SwingUtilities.invokeLater(() -> {
            filteredCountValueLabel.setText(String.valueOf(Math.min(entriesCount, logsTable.getRowSorter().getViewRowCount())));
            totalCountValueLabel.setText("/" + entriesCount);
        }));
        gbc = createGridConstraints(2, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        resultsFilterBar.add(totalCountValueLabel, gbc);

        JLabel resultsCountSeparator = new JLabel("│");
        gbc = createGridConstraints(3, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(0, 10, 0, 10);
        resultsFilterBar.add(resultsCountSeparator, gbc);

        JLabel searchLabel = new JLabel(getLocaleString("logger-searchBar-label"));
        gbc = createGridConstraints(4, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(0, 0, 0, 5);
        resultsFilterBar.add(searchLabel, gbc);

        this.searchField = new JTextField();
        gbc = createGridConstraints(5, 0, 1, 0, GridBagConstraints.HORIZONTAL);
        resultsFilterBar.add(this.searchField, gbc);

        this.regexCheckbox = new JCheckBox(getLocaleString(LogsTableModel.Column.REGEX.getLocaleKey()));
        this.regexCheckbox.setSelected(true);
        gbc = createGridConstraints(6, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(0, 10, 0, 0);
        resultsFilterBar.add(this.regexCheckbox, gbc);

        this.matchCheckbox = new JCheckBox(getLocaleString(LogsTableModel.Column.MATCH.getLocaleKey()));
        this.matchCheckbox.setSelected(true);
        gbc = createGridConstraints(7, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(0, 10, 0, 0);
        resultsFilterBar.add(this.matchCheckbox, gbc);

        this.URLCheckbox = new JCheckBox(getLocaleString(LogsTableModel.Column.URL.getLocaleKey()));
        this.URLCheckbox.setSelected(true);
        gbc = createGridConstraints(8, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(0, 10, 0, 0);
        resultsFilterBar.add(this.URLCheckbox, gbc);

        JLabel filterUniqueSeparator = new JLabel("│");
        gbc = createGridConstraints(9, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(0, 10, 0, 0);
        resultsFilterBar.add(filterUniqueSeparator, gbc);

        this.UniqueCheckbox = new JCheckBox(getLocaleString("logger-uniqueResults-label"));
        this.UniqueCheckbox.setSelected(false);
        gbc = createGridConstraints(10, 0, 0, 0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(0, 10, 0, 0);
        resultsFilterBar.add(this.UniqueCheckbox, gbc);

        Runnable doUpdateRowFilter = () -> updateRowFilter(
                searchField.getText(),
                regexCheckbox.isSelected(),
                matchCheckbox.isSelected(),
                URLCheckbox.isSelected(),
                UniqueCheckbox.isSelected()
        );
        regexCheckbox.addActionListener(event -> doUpdateRowFilter.run());
        matchCheckbox.addActionListener(event -> doUpdateRowFilter.run());
        URLCheckbox.addActionListener(event -> doUpdateRowFilter.run());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                doUpdateRowFilter.run();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                doUpdateRowFilter.run();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                doUpdateRowFilter.run();
            }
        });
        UniqueCheckbox.addActionListener(event -> doUpdateRowFilter.run());

        return resultsFilterBar;
    }

    /**
     * Create tool source filter panel with checkboxes
     */
    private JPanel createToolSourceFilterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setBorder(BorderFactory.createTitledBorder("Tool Sources"));

        String[] tools = {"ALL", "TARGET", "PROXY", "INTRUDER", "REPEATER", "SCANNER", "SEQUENCER", "EXTENSIONS"};
        List<JCheckBox> checkboxes = new ArrayList<>();
        JCheckBox[] allCheckboxRef = new JCheckBox[1]; // Reference to ALL checkbox

        for (String tool : tools) {
            JCheckBox checkbox = new JCheckBox(tool);
            checkbox.setSelected(tool.equals("PROXY")); // Default to PROXY only

            if (tool.equals("ALL")) {
                allCheckboxRef[0] = checkbox;
                // ALL checkbox: toggle all others
                checkbox.addActionListener(e -> {
                    boolean isSelected = checkbox.isSelected();
                    for (JCheckBox cb : checkboxes) {
                        if (!cb.getText().equals("ALL")) {
                            cb.setSelected(isSelected);
                        }
                    }
                    // Update scanner options
                    updateToolSources(checkboxes);
                });
            } else {
                // Other checkboxes: update ALL if needed
                checkbox.addActionListener(e -> {
                    // If any non-ALL checkbox is deselected, deselect ALL
                    if (!checkbox.isSelected() && allCheckboxRef[0] != null) {
                        allCheckboxRef[0].setSelected(false);
                    }
                    // If all non-ALL checkboxes are selected, select ALL
                    boolean allSelected = checkboxes.stream()
                        .filter(cb -> !cb.getText().equals("ALL"))
                        .allMatch(JCheckBox::isSelected);
                    if (allSelected && allCheckboxRef[0] != null) {
                        allCheckboxRef[0].setSelected(true);
                    }
                    // Update scanner options
                    updateToolSources(checkboxes);
                });
            }

            checkboxes.add(checkbox);
            panel.add(checkbox);
        }

        return panel;
    }

    private void updateToolSources(List<JCheckBox> checkboxes) {
        String selected = checkboxes.stream()
            .filter(JCheckBox::isSelected)
            .map(JCheckBox::getText)
            .collect(Collectors.joining(","));
        mainUI.getScannerOptions().setToolSources(selected); // Allow empty to block all
    }

    /**
     * Create origin filter panel with checkboxes
     */
    private JPanel createOriginFilterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setBorder(BorderFactory.createTitledBorder("Origin"));

        ButtonGroup group = new ButtonGroup();
        JRadioButton requestsRadio = new JRadioButton("Requests");
        JRadioButton responsesRadio = new JRadioButton("Responses");
        JRadioButton bothRadio = new JRadioButton("Both");
        bothRadio.setSelected(true);

        group.add(requestsRadio);
        group.add(responsesRadio);
        group.add(bothRadio);

        requestsRadio.addActionListener(e -> mainUI.getScannerOptions().setOriginFilter("REQUESTS"));
        responsesRadio.addActionListener(e -> mainUI.getScannerOptions().setOriginFilter("RESPONSES"));
        bothRadio.addActionListener(e -> mainUI.getScannerOptions().setOriginFilter("BOTH"));

        panel.add(requestsRadio);
        panel.add(responsesRadio);
        panel.add(bothRadio);

        return panel;
    }

    private JPanel createAnalysisBar(JScrollPane logEntriesPane) {
        JPanel leftSidePanel;
        JPanel rightSidePanel;
        JPanel analysisBar;
        GridBagConstraints gbc;

        analysisBar = new JPanel();
        analysisBar.setLayout(new GridBagLayout());

        // Left side panel - Scan History, Limit, Enable Real-time
        leftSidePanel = new JPanel();
        leftSidePanel.setLayout(new GridBagLayout());
        leftSidePanel.setPreferredSize(new Dimension(450, 40));
        gbc = createGridConstraints(0, 0, 0.0, 0.0, GridBagConstraints.VERTICAL);
        gbc.anchor = GridBagConstraints.WEST;
        analysisBar.add(leftSidePanel, gbc);

        // Scan History button
        JButton analysisButton = createAnalysisButton();
        analysisButton.setPreferredSize(new Dimension(120, 28));
        gbc = createGridConstraints(0, 0, 0.0, 0.0, GridBagConstraints.NONE);
        gbc.insets = new Insets(0, 5, 0, 10);
        gbc.anchor = GridBagConstraints.CENTER;
        leftSidePanel.add(analysisButton, gbc);

        // History limit label and spinner
        JLabel limitLabel = new JLabel("Limit:");
        gbc = createGridConstraints(1, 0, 0.0, 0.0, GridBagConstraints.NONE);
        gbc.insets = new Insets(0, 0, 0, 5);
        gbc.anchor = GridBagConstraints.CENTER;
        leftSidePanel.add(limitLabel, gbc);

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(-1, -1, 100000, 10);
        JSpinner limitSpinner = new JSpinner(spinnerModel);
        limitSpinner.setPreferredSize(new Dimension(80, 28));
        limitSpinner.setToolTipText("Number of recent requests to scan (-1 for all)");
        limitSpinner.setValue(mainUI.getScannerOptions().getHistoryScanLimit());
        limitSpinner.addChangeListener(e -> {
            mainUI.getScannerOptions().setHistoryScanLimit((Integer) limitSpinner.getValue());
            mainUI.getScannerOptions().saveToPersistentStorage();
        });
        gbc = createGridConstraints(2, 0, 0.0, 0.0, GridBagConstraints.NONE);
        gbc.insets = new Insets(0, 0, 0, 15);
        gbc.anchor = GridBagConstraints.CENTER;
        leftSidePanel.add(limitSpinner, gbc);

        // Real-time analysis toggle button (Burp Intercept style)
        JToggleButton realtimeToggle = new JToggleButton("Real-time Analysis");
        realtimeToggle.setPreferredSize(new Dimension(150, 28));
        realtimeToggle.setSelected(mainUI.getScannerOptions().isRealtimeAnalysisEnabled());
        realtimeToggle.setToolTipText("Automatically analyze HTTP traffic in real-time");

        // Style the toggle button like Burp's Intercept button
        realtimeToggle.setFocusPainted(false);
        realtimeToggle.setBackground(realtimeToggle.isSelected() ? new Color(51, 122, 183) : null);
        realtimeToggle.setForeground(realtimeToggle.isSelected() ? Color.WHITE : null);
        realtimeToggle.setOpaque(true);

        realtimeToggle.addActionListener(e -> {
            // Disable button to prevent race condition
            realtimeToggle.setEnabled(false);
            boolean enabled = realtimeToggle.isSelected();
            mainUI.getScannerOptions().setRealtimeAnalysisEnabled(enabled);
            mainUI.getScannerOptions().saveToPersistentStorage();
            mainUI.toggleRealtimeAnalysis(enabled);

            // Update button style and re-enable
            realtimeToggle.setBackground(enabled ? new Color(51, 122, 183) : null);
            realtimeToggle.setForeground(enabled ? Color.WHITE : null);
            realtimeToggle.setEnabled(true);
        });
        gbc = createGridConstraints(3, 0, 0.0, 0.0, GridBagConstraints.NONE);
        gbc.insets = new Insets(0, 0, 0, 10);
        gbc.anchor = GridBagConstraints.CENTER;
        leftSidePanel.add(realtimeToggle, gbc);

        // Progress bar (center)
        JProgressBar progressBar = new JProgressBar(0, 1);
        progressBar.setStringPainted(true);
        regexScanner.setProgressBar(progressBar);
        gbc = createGridConstraints(1, 0, 1.0, 0.0, GridBagConstraints.HORIZONTAL);
        gbc.insets = new Insets(0, 15, 0, 15);
        analysisBar.add(progressBar, gbc);

        // Right side panel - Clear Logs, Export
        rightSidePanel = new JPanel();
        rightSidePanel.setLayout(new GridBagLayout());
        rightSidePanel.setPreferredSize(new Dimension(270, 40));
        gbc = createGridConstraints(2, 0, 0.0, 0.0, GridBagConstraints.VERTICAL);
        gbc.anchor = GridBagConstraints.EAST;
        analysisBar.add(rightSidePanel, gbc);

        JButton clearLogsButton = createClearLogsButton(logEntriesPane);
        clearLogsButton.setPreferredSize(new Dimension(120, 28));
        gbc = createGridConstraints(0, 0, 0.0, 0.0, GridBagConstraints.NONE);
        gbc.insets = new Insets(0, 0, 0, 5);
        gbc.anchor = GridBagConstraints.CENTER;
        rightSidePanel.add(clearLogsButton, gbc);

        JToggleButton exportLogsButton = createExportLogsButton();
        exportLogsButton.setPreferredSize(new Dimension(140, 28));
        gbc = createGridConstraints(1, 0, 0.0, 0.0, GridBagConstraints.NONE);
        gbc.insets = new Insets(0, 0, 0, 5);
        gbc.anchor = GridBagConstraints.CENTER;
        rightSidePanel.add(exportLogsButton, gbc);

        return analysisBar;
    }

    /**
     * Filter rows of LogsTable that contains text string
     *
     * @param text          text to search
     * @param includeRegex  if true, also search in Regex column
     * @param includeMatch  if true, also search in Match column
     * @param includeURL    if true, also search in URL column
     * @param uniqueResults if true, remove duplicate results
     */
    private void updateRowFilter(String text, boolean includeRegex, boolean includeMatch, boolean includeURL, boolean uniqueResults) {
        SwingUtilities.invokeLater(() -> {
            // hashmap to keep track of unique rows in the table
            final HashSet<Integer> uniqueResultsMap = new HashSet<>();

            logsTableRowSorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends LogsTableModel, ? extends Integer> entry) {
                    // Filter by importance level first
                    LogEntity logEntity = logEntriesManager.get(entry.getIdentifier());
                    if (logEntity != null && logEntity.getRegexEntity() != null) {
                        int importance = logEntity.getRegexEntity().getImportance();
                        if (!mainUI.getScannerOptions().getSelectedImportanceLevels().contains(importance)) {
                            return false;
                        }
                    }

                    if (uniqueResults) {
                        int hashcode = entry.getModel().getRowHashcode(entry.getIdentifier());
                        if (uniqueResultsMap.contains(hashcode)) return false;
                        uniqueResultsMap.add(hashcode);
                    }
                    if (text.isBlank()) {
                        return true;
                    }
                    List<LogsTableModel.Column> places = new ArrayList<>();
                    if (includeRegex) places.add(LogsTableModel.Column.REGEX);
                    if (includeMatch) places.add(LogsTableModel.Column.MATCH);
                    if (includeURL) places.add(LogsTableModel.Column.URL);
                    return places.stream().anyMatch(column -> entry.getStringValue(column.getIndex()).toLowerCase().contains(text.toLowerCase()));
                }
            });
        });
    }

    /**
     * Called when importance levels change to refresh the filter
     */
    private void refreshFilterForImportance() {
        if (searchField != null) {
            updateRowFilter(
                searchField.getText(),
                regexCheckbox.isSelected(),
                matchCheckbox.isSelected(),
                URLCheckbox.isSelected(),
                UniqueCheckbox.isSelected()
            );
        }
    }

    /**
     * Creates a button that handles the analysis of the burp's http history
     *
     * @return the analysis button
     */
    private JButton createAnalysisButton() {
        JButton analysisButton = new JButton();
        String startAnalysisText = "Scan History"; // Changed from locale string
        analysisButton.putClientProperty("initialText", startAnalysisText);
        analysisButton.setText(startAnalysisText);
        analysisButton.addActionListener(actionEvent -> {
            if (!isAnalysisRunning) {
                startAnalysisAction(analysisButton);
            } else {
                stopAnalysisAction(analysisButton);
            }
        });
        return analysisButton;
    }

    private void stopAnalysisAction(JButton analysisButton) {
        if (Objects.isNull(analyzeProxyHistoryThread)) return;

        SwingUtilities.invokeLater(() -> {
            analysisButton.setEnabled(false);
            analysisButton.setText(getLocaleString("logger-analysis-stopping"));
        });
        regexScanner.setInterruptScan(true);

        new Thread(() -> {
            try {
                analyzeProxyHistoryThread.join();
                regexScanner.setInterruptScan(false);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            SwingUtilities.invokeLater(() -> {
                analysisButton.setEnabled(true);
                analysisButton.setText(getLocaleString("logger-analysis-start"));
            });
        }).start();
    }

    private void startAnalysisAction(JButton analysisButton) {
        this.preAnalysisOperations();
        isAnalysisRunning = true;
        analyzeProxyHistoryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                setupScan();
                startScan();
                finalizeScan();
            }

            private void setupScan() {
                SwingUtilities.invokeLater(() -> analysisButton.setText(getLocaleString("logger-analysis-stop")));
            }

            private void startScan() {
                Consumer<LogEntity> addLogEntryCallback = LoggerUtils.createAddLogEntryCallback(logEntriesManager, loggerLock, logsTableModel);
                regexScanner.analyzeProxyHistory(addLogEntryCallback);
            }

            private void finalizeScan() {
                SwingUtilities.invokeLater(() -> analysisButton.setText((String) analysisButton.getClientProperty("initialText")));
                analyzeProxyHistoryThread = null;
                isAnalysisRunning = false;
                LoggerTab.this.postAnalysisOperations();
            }
        });
        analyzeProxyHistoryThread.start();

        SwingUtilities.invokeLater(() -> {
            logsTable.validate();
            logsTable.repaint();
        });
    }

    private JScrollPane createLogEntriesTable() {
        logsTableModel = new LogsTableModel(logEntriesManager);
        this.originalRequestViewer = this.mainUI.getBurpApi().userInterface().createHttpRequestEditor();
        this.originalResponseViewer = this.mainUI.getBurpApi().userInterface().createHttpResponseEditor();
        this.logsTable = new LogsTable(logsTableModel, logEntriesManager, this.originalRequestViewer, this.originalResponseViewer);
        logsTable.setAutoCreateRowSorter(false);
        logsTableRowSorter = new TableRowSorter<>(logsTableModel);
        logsTable.setRowSorter(logsTableRowSorter);

        // when you right-click on a logTable entry, it will appear a context menu defined here
        MouseAdapter contextMenu = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onMouseEvent(e);
            }

            private void onMouseEvent(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = logsTable.getSelectedRow();
                    if (row == -1) return;
                    int realRow = logsTable.convertRowIndexToModel(row);
                    LogEntity logEntry = logEntriesManager.get(realRow);

                    if (logEntry == null) return;

                    if (e.getComponent() instanceof LogsTable) {
                        new LogsTableContextMenu(logEntry, logEntriesManager, originalRequestViewer, originalResponseViewer, logsTableModel, logsTable, mainUI.getBurpApi(), isAnalysisRunning)
                                .show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        };
        logsTable.addMouseListener(contextMenu);

        return new JScrollPane(logsTable);
    }

    private List<LogsTableModel.Column> getExportableColumns() {
        return List.of(
                LogsTableModel.Column.MATCH,
                LogsTableModel.Column.REGEX,
                LogsTableModel.Column.URL
        );
    }

    private JToggleButton createExportLogsButton() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem itemToCSV = new JMenuItem(getLocaleString("common-toCSV"));
        itemToCSV.addActionListener(actionEvent -> {
            String csvFile = SwingUtils.selectFile(List.of("CSV"), false);
            if (csvFile.isBlank()) return;

            java.util.List<String> lines = new ArrayList<>();

            List<LogsTableModel.Column> columns = getExportableColumns();
            String header = columns.stream()
                    .map(LogsTableModel.Column::getNameFormatted)
                    .map(s -> '"' + s + '"')
                    .collect(Collectors.joining(","));
            lines.add(header);

            for (int i = 0; i < logsTable.getRowCount(); i++) {
                final int rowIndex = i;
                String line = columns.stream()
                        .map(LogsTableModel.Column::getIndex)
                        .map(columnIdx -> logsTableModel.getValueAt(logsTable.convertRowIndexToModel(rowIndex), columnIdx))
                        .map(cellValue -> cellValue.toString().replaceAll("\"", "\"\""))
                        .map(s -> '"' + s + '"')
                        .collect(Collectors.joining(","));
                lines.add(line);
            }

            FileUtils.writeLinesToFile(csvFile, lines);
        });
        menu.add(itemToCSV);

        JMenuItem itemToJSON = new JMenuItem(getLocaleString("common-toJSON"));
        itemToJSON.addActionListener(actionEvent -> {
            String jsonFile = SwingUtils.selectFile(List.of("JSON"), false);
            if (jsonFile.isBlank()) return;

            List<LogsTableModel.Column> fields = getExportableColumns();
            List<JsonObject> lines = new ArrayList<>();

            for (int i = 0; i < logsTable.getRowCount(); i++) {
                JsonObject json = new JsonObject();
                for (LogsTableModel.Column column : fields) {
                    json.addProperty(column.getNameFormatted(), logsTableModel.getValueAt(logsTable.convertRowIndexToModel(i), column.getIndex()).toString());
                }
                lines.add(json);
            }

            String json = createGsonBuilder().toJson(lines, (new TypeToken<ArrayList<JsonObject>>() {
            }).getType());
            FileUtils.writeLinesToFile(jsonFile, List.of(json));
        });
        menu.add(itemToJSON);

        PopupMenuButton btnExportLogs = new PopupMenuButton(getLocaleString("logger-exportLogs-label"), menu);
        btnExportLogs.setToolTipText("Export logged findings to CSV or JSON format");
        btnExportLogs.putClientProperty("analysisDependent", "1");

        return btnExportLogs;
    }

    private JButton createClearLogsButton(JScrollPane scrollPaneLogger) {
        JButton btnClearLogs = new JButton(getLocaleString("logger-clearLogs-label"));
        btnClearLogs.setToolTipText("Clear all logged findings from the table");
        btnClearLogs.addActionListener(e -> {
            int dialog = JOptionPane.showConfirmDialog(
                    null,
                    getLocaleString("logger-clearLogs-message"),
                    getLocaleString("logger-clearLogs-title"),
                    JOptionPane.YES_NO_OPTION);
            if (dialog == JOptionPane.YES_OPTION) {
                if (logEntriesManager != null) {
                    logEntriesManager.clear();
                }
                if (logsTableModel != null) {
                    logsTableModel.fireTableDataChanged();
                }

                if (originalResponseViewer != null) {
                    originalResponseViewer.setResponse(HttpResponse.httpResponse(""));
                    originalResponseViewer.setSearchExpression("");
                }
                if (originalRequestViewer != null) {
                    originalRequestViewer.setRequest(HttpRequest.httpRequest(""));
                }
            }

            scrollPaneLogger.validate();
            scrollPaneLogger.repaint();
        });

        btnClearLogs.putClientProperty("analysisDependent", "1");
        return btnClearLogs;
    }

    private GridBagConstraints createGridConstraints(int gridx, int gridy, double weightx, double weighty, int fill) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbc.fill = fill;
        return gbc;
    }

    @Override
    public JPanel getPanel() {
        return this.panel;
    }

    @Override
    public String getTabName() {
        return TAB_NAME;
    }

    /**
     * Get the regex scanner instance for real-time analysis integration
     */
    public RegexScanner getRegexScanner() {
        return this.regexScanner;
    }

    /**
     * Get the logs table model for real-time updates
     */
    public LogsTableModel getLogsTableModel() {
        return this.logsTableModel;
    }

    /**
     * Create statistics panel showing breakdown by importance
     */
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180)),
            "Findings Statistics",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            UIConstants.FONT_DIALOG_BOLD_12
        ));

        String[] levels = ImportanceUtils.getAllLabels();
        Color[] colors = ImportanceColorScheme.getAllColors();

        JLabel[] countLabels = new JLabel[6];
        for (int i = 0; i < 6; i++) {
            final Color bgColor = colors[i];

            // Create a custom panel that forces painting
            JPanel levelPanel = new JPanel() {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    super.paintComponent(g);
                    g.setColor(bgColor);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            levelPanel.setPreferredSize(UIConstants.STATS_BOX);
            levelPanel.setMinimumSize(UIConstants.STATS_BOX);
            levelPanel.setBackground(bgColor);
            levelPanel.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 2));

            JLabel label = new JLabel(levels[i] + ": 0", JLabel.CENTER);
            label.setForeground(Color.BLACK);
            label.setFont(UIConstants.FONT_DIALOG_BOLD_12);
            countLabels[i] = label;
            levelPanel.add(label);
            panel.add(levelPanel);
        }

        // Update counts when entries change
        logEntriesManager.subscribeChangeListener(entriesCount -> SwingUtilities.invokeLater(() -> {
            int[] counts = new int[6];
            for (int i = 0; i < logEntriesManager.size(); i++) {
                LogEntity entity = logEntriesManager.get(i);
                if (entity == null || entity.getRegexEntity() == null) continue;
                int importance = entity.getRegexEntity().getImportance();
                if (importance >= 0 && importance <= 5) {
                    counts[importance]++;
                }
            }
            for (int i = 0; i < 6; i++) {
                countLabels[i].setText(levels[i] + ": " + counts[i]);
            }
        }));

        return panel;
    }
}
