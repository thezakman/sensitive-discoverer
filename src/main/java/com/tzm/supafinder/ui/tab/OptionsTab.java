package com.tzm.supafinder.ui.tab;

import com.tzm.supafinder.MainUI;
import com.tzm.supafinder.RegexSeeder;
import com.tzm.supafinder.event.OptionsScannerUpdateListener;
import com.tzm.supafinder.event.OptionsScannerUpdateMaxSizeListener;
import com.tzm.supafinder.event.OptionsScannerUpdateNumThreadsListener;
import com.tzm.supafinder.model.RegexScannerOptions;
import com.tzm.supafinder.ui.RegexListPanel;
import com.tzm.supafinder.utils.ImportanceColorScheme;
import com.tzm.supafinder.utils.ImportanceUtils;
import com.tzm.supafinder.utils.UIConstants;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import static com.tzm.supafinder.utils.Messages.getLocaleString;

public class OptionsTab implements ApplicationTab {
    private static final String TAB_NAME = getLocaleString("tab-options");
    private final JPanel panel;
    private final RegexScannerOptions scannerOptions;
    private final List<Runnable> resetOptionsListeners = new ArrayList<>();
    private RegexListPanel generalListPanel;
    private RegexListPanel extensionsListPanel;
    private JLabel globalRegexCounterLabel;
    private int yamlPatternsCount = 0; // Track YAML-imported patterns

    public OptionsTab(RegexScannerOptions scannerOptions) {
        this.scannerOptions = scannerOptions;

        // leave as last call
        this.panel = this.createPanel();
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
     * Options panel hierarchy:
     * <pre>
     * box [BorderLayout]
     * +--boxHeader [GridBagLayout]
     *    +--...configuration panels
     * +--boxCenter [GridBagLayout]
     *    +--general [BorderLayout]
     *    |  +--generalHeader [GridBagLayout]
     *    |  +--generalBody [BorderLayout]
     *    |     +--generalBodyRight [GridBagLayout]
     *    |     +--generalBodyCenter [GridBagLayout]
     *    +--extensions [BorderLayout]
     *       +--extensionsHeader [GridBagLayout]
     *       +--extensionsBody [BorderLayout]
     *          +--extensionsBodyRight [GridBagLayout]
     *          +--extensionsBodyCenter [GridBagLayout]
     * </pre>
     *
     * @return The panel for the Options Tab
     */
    private JPanel createPanel() {
        JPanel box;
        JPanel boxHeader;
        JPanel boxCenter;

        OptionsScannerUpdateListener threadNumListener = new OptionsScannerUpdateNumThreadsListener(scannerOptions);
        OptionsScannerUpdateListener responseSizeListener = new OptionsScannerUpdateMaxSizeListener(scannerOptions);

        boxHeader = new JPanel(new GridBagLayout());
        createConfigurationPanels(boxHeader, threadNumListener, responseSizeListener);

        boxCenter = new JPanel(new GridBagLayout());
        createListsPanels(boxCenter);

        box = new JPanel(new BorderLayout(0, 0));
        box.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        box.add(boxHeader, BorderLayout.NORTH);
        box.add(boxCenter, BorderLayout.CENTER);

        // Add statistics panel at bottom
        JPanel statsPanel = createStatsPanel();
        box.add(statsPanel, BorderLayout.SOUTH);

        box.putClientProperty("analysisDependent", "1");

        return box;
    }

    private void createListsPanels(JPanel boxCenter) {
        GridBagConstraints gbc;

        generalListPanel = new RegexListPanel(
                getLocaleString("options-generalList-title"),
                getLocaleString("options-generalList-description"),
                this.scannerOptions.getGeneralRegexList(),
                RegexSeeder::getGeneralRegexes);
        // Connect callback to update counter when general list changes
        generalListPanel.setOnListChangedCallback(this::updateGlobalRegexCounter);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        boxCenter.add(generalListPanel.getPanel(), gbc);

        extensionsListPanel = new RegexListPanel(
                getLocaleString("options-extensionsList-title"),
                getLocaleString("options-extensionsList-description"),
                this.scannerOptions.getExtensionsRegexList(),
                RegexSeeder::getExtensionRegexes);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        boxCenter.add(extensionsListPanel.getPanel(), gbc);
    }

    private void createConfigurationPanels(JPanel boxHeader, OptionsScannerUpdateListener threadNumListener, OptionsScannerUpdateListener responseSizeListener) {
        GridBagConstraints gbc;

        final JPanel filterPanelWrapper = new JPanel(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.ipadx = 5;
        gbc.ipady = 5;
        boxHeader.add(filterPanelWrapper, gbc);
        final JPanel filterPanel = createConfigurationFilterPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        filterPanelWrapper.add(filterPanel, gbc);
        final JButton resetAllOptionsButton = new JButton(getLocaleString("options-resetAll-button"));
        resetAllOptionsButton.addActionListener(e -> {
            scannerOptions.resetToDefaults(true, false);
            resetOptionsListeners.forEach(Runnable::run);
        });
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 2, 0, 2);
        filterPanelWrapper.add(resetAllOptionsButton, gbc);

        final JPanel scannerPanel = createConfigurationScannerPanel(threadNumListener, responseSizeListener);
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.ipadx = 5;
        gbc.ipady = 5;
        gbc.insets = new Insets(0, 20, 0, 0);
        boxHeader.add(scannerPanel, gbc);

        // YAML Import Panel
        final JPanel yamlPanel = createYAMLImportPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.ipadx = 5;
        gbc.ipady = 5;
        gbc.insets = new Insets(0, 20, 0, 0);
        boxHeader.add(yamlPanel, gbc);

        // Importance Filter Panel
        final JPanel importancePanel = createImportanceFilterPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.ipadx = 5;
        gbc.ipady = 5;
        gbc.insets = new Insets(0, 20, 0, 0);
        boxHeader.add(importancePanel, gbc);

        final JPanel spacerLeft = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        boxHeader.add(spacerLeft, gbc);
        final JSeparator middleSeparator = new JSeparator();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 6;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 0, 0, 0);
        boxHeader.add(middleSeparator, gbc);
        final JPanel spacerRight = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        boxHeader.add(spacerRight, gbc);
    }

    private JPanel createConfigurationScannerPanel(OptionsScannerUpdateListener threadNumListener, OptionsScannerUpdateListener responseSizeListener) {
        final JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.gray, 1),
                getLocaleString("options-scanner-title"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION,
                MainUI.UIOptions.H2_FONT,
                MainUI.UIOptions.ACCENT_COLOR
        ));

        createOptionThreadsNumber(panel, threadNumListener);
        createOptionMaxResponseSize(panel, responseSizeListener);

        return panel;
    }

    /**
     * Create YAML import panel
     */
    private JPanel createYAMLImportPanel() {
        GridBagConstraints gbc;
        final JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.gray, 1),
                "YAML Import",
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION,
                MainUI.UIOptions.H2_FONT,
                MainUI.UIOptions.ACCENT_COLOR
        ));

        // Import single YAML button
        JButton importSingleButton = new JButton("Import Single YAML");
        importSingleButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("YAML files", "yaml", "yml"));
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                try {
                    String filepath = fileChooser.getSelectedFile().getAbsolutePath();
                    List<com.tzm.supafinder.model.RegexEntity> duplicates =
                        com.tzm.supafinder.utils.FileUtils.importRegexListFromYAML(
                            filepath, scannerOptions.getGeneralRegexList());

                    int imported = 1 - duplicates.size();
                    if (imported > 0) {
                        yamlPatternsCount += imported;
                    }
                    // Update global regex counter
                    updateGlobalRegexCounter();
                    // Refresh the regex list panels
                    generalListPanel.refresh();
                    String message = imported > 0 ?
                        "Successfully imported " + imported + " pattern" :
                        "Pattern already exists (duplicate)";
                    JOptionPane.showMessageDialog(panel, message, "YAML Import", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(panel, "Error importing YAML: " + ex.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        panel.add(importSingleButton, gbc);

        // Import folder button
        JButton importFolderButton = new JButton("Import YAML Folder");
        importFolderButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                String dirpath = fileChooser.getSelectedFile().getAbsolutePath();

                // Create progress dialog
                JDialog progressDialog = new JDialog();
                progressDialog.setTitle("Importing YAML Folder");
                progressDialog.setModal(true);
                progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                progressDialog.setSize(450, 140);
                progressDialog.setLocationRelativeTo(panel);

                JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
                progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

                JLabel statusLabel = new JLabel("Preparing to import YAML files...");
                progressPanel.add(statusLabel, BorderLayout.NORTH);

                JProgressBar progressBar = new JProgressBar(0, 100);
                progressBar.setStringPainted(true);
                progressBar.setString("0%");
                progressPanel.add(progressBar, BorderLayout.CENTER);

                JLabel detailsLabel = new JLabel(" ");
                progressPanel.add(detailsLabel, BorderLayout.SOUTH);

                progressDialog.add(progressPanel);

                // Background worker for import with progress tracking
                SwingWorker<ImportResult, ProgressUpdate> worker = new SwingWorker<>() {
                    @Override
                    protected ImportResult doInBackground() throws Exception {
                        int sizeBefore = scannerOptions.getGeneralRegexList().size();

                        // Get all YAML files recursively
                        java.io.File dir = new java.io.File(dirpath);
                        List<java.io.File> yamlFilesList = new ArrayList<>();
                        findYamlFilesRecursively(dir, yamlFilesList);

                        int totalFiles = yamlFilesList.size();

                        if (totalFiles == 0) {
                            return new ImportResult(0, 0, "No YAML files found");
                        }

                        java.io.File[] yamlFiles = yamlFilesList.toArray(new java.io.File[0]);

                        publish(new ProgressUpdate(0, totalFiles, 0, "Starting import..."));

                        // Import files with progress tracking
                        List<com.tzm.supafinder.model.RegexEntity> allDuplicates = new ArrayList<>();
                        int filesProcessed = 0;
                        int totalPatternsLoaded = 0;

                        List<String> errorFiles = new ArrayList<>();

                        for (java.io.File file : yamlFiles) {
                            // Check if cancelled
                            if (isCancelled()) {
                                return new ImportResult(0, 0, "Import cancelled by user");
                            }

                            filesProcessed++;
                            int progress = (filesProcessed * 100) / totalFiles;

                            try {
                                // Import this single file
                                List<com.tzm.supafinder.model.RegexEntity> duplicates =
                                    com.tzm.supafinder.utils.FileUtils.importRegexListFromYAML(
                                        file.getAbsolutePath(), scannerOptions.getGeneralRegexList());

                                allDuplicates.addAll(duplicates);
                                int currentSize = scannerOptions.getGeneralRegexList().size() - sizeBefore;
                                totalPatternsLoaded = currentSize + allDuplicates.size();

                                publish(new ProgressUpdate(
                                    progress,
                                    totalFiles,
                                    filesProcessed,
                                    totalPatternsLoaded + " patterns loaded"
                                ));
                            } catch (Exception ex) {
                                // Track files that failed to import
                                String errorMsg = file.getName() + ": " +
                                    (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                                errorFiles.add(errorMsg);

                                publish(new ProgressUpdate(
                                    progress,
                                    totalFiles,
                                    filesProcessed,
                                    "Error in " + file.getName()
                                ));
                            }
                        }

                        int imported = scannerOptions.getGeneralRegexList().size() - sizeBefore;
                        return new ImportResult(imported, allDuplicates.size(), null, errorFiles);
                    }

                    @Override
                    protected void process(List<ProgressUpdate> chunks) {
                        if (!chunks.isEmpty()) {
                            ProgressUpdate latest = chunks.get(chunks.size() - 1);
                            progressBar.setValue(latest.percentage);
                            progressBar.setString(latest.percentage + "%");
                            statusLabel.setText("Processing file " + latest.filesProcessed + " of " + latest.totalFiles);
                            detailsLabel.setText(latest.message);
                        }
                    }

                    @Override
                    protected void done() {
                        progressDialog.dispose();
                        try {
                            ImportResult result = get();
                            if (result.error != null) {
                                JOptionPane.showMessageDialog(panel, result.error, "YAML Folder Import", JOptionPane.WARNING_MESSAGE);
                                return;
                            }
                            // Increment YAML counter
                            yamlPatternsCount += result.imported;
                            // Update global regex counter
                            updateGlobalRegexCounter();
                            // Refresh the regex list panels
                            generalListPanel.refresh();

                            StringBuilder message = new StringBuilder();
                            message.append("Successfully imported ").append(result.imported).append(" pattern(s)");
                            if (result.duplicates > 0) {
                                message.append(" (").append(result.duplicates).append(" duplicates skipped)");
                            }

                            // Show errors if any
                            if (result.errorFiles != null && !result.errorFiles.isEmpty()) {
                                message.append("\n\nErrors in ").append(result.errorFiles.size()).append(" file(s):\n");
                                int maxErrors = Math.min(10, result.errorFiles.size());
                                for (int i = 0; i < maxErrors; i++) {
                                    message.append("- ").append(result.errorFiles.get(i)).append("\n");
                                }
                                if (result.errorFiles.size() > maxErrors) {
                                    message.append("... and ").append(result.errorFiles.size() - maxErrors).append(" more");
                                }
                                JOptionPane.showMessageDialog(panel, message.toString(), "YAML Folder Import (with errors)", JOptionPane.WARNING_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(panel, message.toString(), "YAML Folder Import", JOptionPane.INFORMATION_MESSAGE);
                            }
                        } catch (java.util.concurrent.CancellationException ex) {
                            JOptionPane.showMessageDialog(panel, "Import was cancelled", "YAML Folder Import", JOptionPane.WARNING_MESSAGE);
                        } catch (Exception ex) {
                            String errorMsg = "Error importing YAML folder: ";
                            if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                                errorMsg += ex.getCause().getMessage();
                            } else if (ex.getMessage() != null) {
                                errorMsg += ex.getMessage();
                            } else {
                                errorMsg += "Unknown error";
                            }
                            JOptionPane.showMessageDialog(panel, errorMsg, "Import Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };

                worker.execute();
                progressDialog.setVisible(true);
            }
        });
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        panel.add(importFolderButton, gbc);

        // Auto-watch folder path
        JLabel watchLabel = new JLabel("Auto-watch Path:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 2, 2, 2);
        panel.add(watchLabel, gbc);

        JTextField watchPathField = new JTextField(scannerOptions.getYamlAutoWatchPath());
        watchPathField.setToolTipText("Path to folder for auto-watching YAML files");

        // Add visual validation - green border for valid path, red for invalid, gray for empty
        Runnable validatePath = () -> {
            String path = watchPathField.getText().trim();
            if (path.isEmpty()) {
                watchPathField.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                watchPathField.setToolTipText("Path to folder for auto-watching YAML files");
            } else {
                java.io.File file = new java.io.File(path);
                if (file.exists() && file.isDirectory()) {
                    watchPathField.setBorder(BorderFactory.createLineBorder(new Color(34, 139, 34), 2));
                    watchPathField.setToolTipText("Valid directory: " + path);
                } else {
                    watchPathField.setBorder(BorderFactory.createLineBorder(new Color(220, 53, 69), 2));
                    watchPathField.setToolTipText("Invalid path or not a directory");
                }
            }
        };

        watchPathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validatePath.run();
                scannerOptions.setYamlAutoWatchPath(watchPathField.getText().trim());
                scannerOptions.saveToPersistentStorage();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                validatePath.run();
                scannerOptions.setYamlAutoWatchPath(watchPathField.getText().trim());
                scannerOptions.saveToPersistentStorage();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                validatePath.run();
                scannerOptions.setYamlAutoWatchPath(watchPathField.getText().trim());
                scannerOptions.saveToPersistentStorage();
            }
        });

        // Trigger initial validation
        validatePath.run();

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        panel.add(watchPathField, gbc);

        // Browse button
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (!watchPathField.getText().isEmpty()) {
                fileChooser.setCurrentDirectory(new java.io.File(watchPathField.getText()));
            }
            int result = fileChooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION) {
                String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();
                watchPathField.setText(selectedPath);
                scannerOptions.setYamlAutoWatchPath(selectedPath);
            }
        });
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        panel.add(browseButton, gbc);

        // Enable auto-watch checkbox with pattern counter
        JPanel autoWatchPanel = new JPanel(new GridBagLayout());

        JCheckBox autoWatchCheckbox = new JCheckBox("Enable Auto-watch");
        autoWatchCheckbox.setSelected(scannerOptions.isYamlAutoWatchEnabled());
        autoWatchCheckbox.addActionListener(e -> {
            scannerOptions.setYamlAutoWatchEnabled(autoWatchCheckbox.isSelected());
        });
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        autoWatchPanel.add(autoWatchCheckbox, gbc);

        // Pattern counter label (simple, no HTML)
        globalRegexCounterLabel = new JLabel();
        updateGlobalRegexCounter();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 15, 0, 0);
        autoWatchPanel.add(globalRegexCounterLabel, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 2, 2, 2);
        panel.add(autoWatchPanel, gbc);

        return panel;
    }

    /**
     * Create importance filter panel
     */
    private JPanel createImportanceFilterPanel() {
        GridBagConstraints gbc;
        final JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.gray, 1),
                "Importance Filter",
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION,
                MainUI.UIOptions.H2_FONT,
                MainUI.UIOptions.ACCENT_COLOR
        ));

        JLabel label = new JLabel("Select Levels:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 5, 2);
        panel.add(label, gbc);

        String[] levels = {"Debug (0)", "Info (1)", "Low (2)", "Medium (3)", "High (4)", "Critical (5)"};

        for (int i = 0; i < levels.length; i++) {
            final int level = i;
            JCheckBox checkbox = new JCheckBox(levels[i]);
            checkbox.setSelected(scannerOptions.isImportanceLevelSelected(i));
            checkbox.addActionListener(e -> {
                boolean isSelected = checkbox.isSelected();
                scannerOptions.toggleImportanceLevel(level);

                // Activate/deactivate all regexes of this importance level
                scannerOptions.getGeneralRegexList().stream()
                        .filter(regex -> regex.getImportance() == level)
                        .forEach(regex -> regex.setActive(isSelected));

                scannerOptions.getExtensionsRegexList().stream()
                        .filter(regex -> regex.getImportance() == level)
                        .forEach(regex -> regex.setActive(isSelected));

                // Refresh both regex list panels
                if (generalListPanel != null) {
                    generalListPanel.refresh();
                }
                if (extensionsListPanel != null) {
                    extensionsListPanel.refresh();
                }

                scannerOptions.saveToPersistentStorage();
            });

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = i + 1;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(2, 2, 2, 2);
            panel.add(checkbox, gbc);
        }

        return panel;
    }

    private JPanel createConfigurationFilterPanel() {
        GridBagConstraints gbc;
        Runnable setValueFromOptions;

        final JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.gray, 1),
                getLocaleString("options-filters-title"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION,
                MainUI.UIOptions.H2_FONT,
                MainUI.UIOptions.ACCENT_COLOR
        ));

        JCheckBox inScopeCheckbox = new JCheckBox();
        inScopeCheckbox.setText(getLocaleString("options-filters-showOnlyInScopeItems"));
        setValueFromOptions = () -> inScopeCheckbox.getModel().setSelected(scannerOptions.isFilterInScopeCheckbox());
        setValueFromOptions.run();
        inScopeCheckbox.addActionListener(e -> scannerOptions.setFilterInScopeCheckbox(inScopeCheckbox.getModel().isSelected()));
        resetOptionsListeners.add(setValueFromOptions);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(inScopeCheckbox, gbc);

        JCheckBox skipMaxSizeCheckbox = new JCheckBox();
        skipMaxSizeCheckbox.setText(getLocaleString("options-filters-skipResponsesOverSetSize"));
        setValueFromOptions = () -> skipMaxSizeCheckbox.getModel().setSelected(scannerOptions.isFilterSkipMaxSizeCheckbox());
        setValueFromOptions.run();
        skipMaxSizeCheckbox.addActionListener(e -> scannerOptions.setFilterSkipMaxSizeCheckbox(skipMaxSizeCheckbox.getModel().isSelected()));
        resetOptionsListeners.add(setValueFromOptions);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(skipMaxSizeCheckbox, gbc);

        JCheckBox skipMediaTypeCheckbox = new JCheckBox();
        skipMediaTypeCheckbox.setText(getLocaleString("options-filters-skipMediaTypeResponses"));
        setValueFromOptions = () -> skipMediaTypeCheckbox.getModel().setSelected(scannerOptions.isFilterSkipMediaTypeCheckbox());
        setValueFromOptions.run();
        skipMediaTypeCheckbox.addActionListener(e -> scannerOptions.setFilterSkipMediaTypeCheckbox(skipMediaTypeCheckbox.getModel().isSelected()));
        resetOptionsListeners.add(setValueFromOptions);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(skipMediaTypeCheckbox, gbc);

        return panel;
    }

    private void createOptionMaxResponseSize(JPanel containerPanel, OptionsScannerUpdateListener updateListener) {
        GridBagConstraints gbc;


        // current value section
        final JPanel currentValuePanel = new JPanel();
        currentValuePanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(12, 2, 0, 2);
        containerPanel.add(currentValuePanel, gbc);

        final JLabel currentDescriptionPanel = new JLabel();
        currentDescriptionPanel.setText(getLocaleString("options-scanner-currentMaxResponseSize"));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 5);
        currentValuePanel.add(currentDescriptionPanel, gbc);

        final JLabel currentValueLabel = new JLabel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        currentValuePanel.add(currentValueLabel, gbc);


        // update value section
        final JPanel updateValuePanel = new JPanel();
        updateValuePanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(2, 2, 0, 2);
        containerPanel.add(updateValuePanel, gbc);

        final JLabel updateDescriptionLabel = new JLabel();
        updateDescriptionLabel.setText(getLocaleString("options-scanner-updateMaxResponseSize"));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 5);
        updateValuePanel.add(updateDescriptionLabel, gbc);

        final JTextField updateValueField = new JTextField();
        updateValueField.setColumns(8);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 5);
        updateValuePanel.add(updateValueField, gbc);

        final JButton updateValueButton = new JButton();
        updateValueButton.setText(getLocaleString("common-set"));
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        updateValuePanel.add(updateValueButton, gbc);


        // setup values and listener
        Runnable updateLabelText = () -> currentValueLabel.setText(String.valueOf(scannerOptions.getConfigMaxResponseSize()));
        updateLabelText.run();
        updateListener.setCurrentValueLabel(currentValueLabel);
        updateListener.setUpdatedStatusField(updateValueField);
        updateValueButton.addActionListener(updateListener);
        resetOptionsListeners.add(updateLabelText);
    }

    private void createOptionThreadsNumber(JPanel containerPanel, OptionsScannerUpdateListener updateListener) {
        GridBagConstraints gbc;


        // current value section
        final JPanel currentValuePanel = new JPanel();
        currentValuePanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(2, 2, 0, 2);
        containerPanel.add(currentValuePanel, gbc);

        final JLabel currentDescriptionLabel = new JLabel();
        currentDescriptionLabel.setText(getLocaleString("options-scanner-currentNumberOfThreads"));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 5);
        currentValuePanel.add(currentDescriptionLabel, gbc);

        final JLabel currentValueLabel = new JLabel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        currentValuePanel.add(currentValueLabel, gbc);


        // update value section
        final JPanel updateValuePanel = new JPanel();
        updateValuePanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(2, 2, 0, 2);
        containerPanel.add(updateValuePanel, gbc);

        final JLabel updateDescriptionLabel = new JLabel();
        updateDescriptionLabel.setText("%s (1-128):".formatted(getLocaleString("options-scanner-updateNumberOfThreads")));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 5);
        updateValuePanel.add(updateDescriptionLabel, gbc);

        JTextField updateValueField = new JTextField();
        updateValueField.setColumns(4);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 5);
        updateValuePanel.add(updateValueField, gbc);

        JButton updateValueButton = new JButton();
        updateValueButton.setText(getLocaleString("common-set"));
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        updateValuePanel.add(updateValueButton, gbc);


        // setup values and listener
        Runnable updateLabelText = () -> currentValueLabel.setText(String.valueOf(scannerOptions.getConfigNumberOfThreads()));
        updateLabelText.run();
        updateListener.setCurrentValueLabel(currentValueLabel);
        updateListener.setUpdatedStatusField(updateValueField);
        updateValueButton.addActionListener(updateListener);
        resetOptionsListeners.add(updateLabelText);
    }

    /**
     * Create statistics panel showing breakdown by importance
     */
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180)),
            "Regex Statistics by Importance",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            UIConstants.FONT_DIALOG_BOLD_12
        ));

        String[] levels = ImportanceUtils.getAllLabels();
        Color[] colors = ImportanceColorScheme.getAllColors();

        // Calculate initial counts
        int[] counts = new int[6];
        for (com.tzm.supafinder.model.RegexEntity regex : scannerOptions.getGeneralRegexList()) {
            int importance = regex.getImportance();
            if (importance >= 0 && importance <= 5) {
                counts[importance]++;
            }
        }
        for (com.tzm.supafinder.model.RegexEntity regex : scannerOptions.getExtensionsRegexList()) {
            int importance = regex.getImportance();
            if (importance >= 0 && importance <= 5) {
                counts[importance]++;
            }
        }

        for (int i = 0; i < 6; i++) {
            final Color bgColor = colors[i];
            final int count = counts[i];

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

            JLabel label = new JLabel(levels[i] + ": " + count, JLabel.CENTER);
            label.setForeground(Color.BLACK);
            label.setFont(UIConstants.FONT_DIALOG_BOLD_12);
            levelPanel.add(label);
            panel.add(levelPanel);
        }

        return panel;
    }

    /**
     * Update the global regex counter label (shows total YAML patterns loaded)
     */
    private void updateGlobalRegexCounter() {
        if (globalRegexCounterLabel == null) {
            return;
        }

        String text = String.format("Loaded YAMLs: %d", yamlPatternsCount);
        globalRegexCounterLabel.setText(text);
    }

    /**
     * Helper class to hold progress updates during import
     */
    private static class ProgressUpdate {
        final int percentage;
        final int totalFiles;
        final int filesProcessed;
        final String message;

        ProgressUpdate(int percentage, int totalFiles, int filesProcessed, String message) {
            this.percentage = percentage;
            this.totalFiles = totalFiles;
            this.filesProcessed = filesProcessed;
            this.message = message;
        }
    }

    /**
     * Recursively find all YAML files in a directory and its subdirectories
     *
     * @param dir The directory to search
     * @param yamlFiles List to accumulate found YAML files
     */
    private void findYamlFilesRecursively(java.io.File dir, List<java.io.File> yamlFiles) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        java.io.File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (java.io.File file : files) {
            if (file.isDirectory()) {
                // Recursively search subdirectories
                findYamlFilesRecursively(file, yamlFiles);
            } else if (file.isFile()) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                    yamlFiles.add(file);
                }
            }
        }
    }

    /**
     * Helper class to hold import results
     */
    private static class ImportResult {
        final int imported;
        final int duplicates;
        final String error;
        final List<String> errorFiles;

        ImportResult(int imported, int duplicates, String error) {
            this.imported = imported;
            this.duplicates = duplicates;
            this.error = error;
            this.errorFiles = new ArrayList<>();
        }

        ImportResult(int imported, int duplicates, String error, List<String> errorFiles) {
            this.imported = imported;
            this.duplicates = duplicates;
            this.error = error;
            this.errorFiles = errorFiles != null ? errorFiles : new ArrayList<>();
        }
    }
}
