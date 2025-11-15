package com.tzm.supafinder.ui;

import com.tzm.supafinder.MainUI;
import com.tzm.supafinder.model.RegexEntity;
import com.tzm.supafinder.ui.table.RegexListTable;
import com.tzm.supafinder.ui.table.RegexListTableModel;
import com.tzm.supafinder.utils.FileUtils;
import com.tzm.supafinder.utils.SwingUtils;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static com.tzm.supafinder.utils.Messages.getLocaleString;

public class RegexListPanel {
    private final JPanel panel;
    private RegexListTableModel tableModel;
    private JLabel counterLabel;
    private List<RegexEntity> regexEntities;
    private JPanel tabPaneOptions;
    private Runnable onListChangedCallback;

    /**
     * Creates a JPanel to view regexes in a table, and to make operations on these regexes.
     * <br><br>
     * The components are mainly a table to display the regexes and some buttons to do operations on the list.
     * The input regexEntities is modified accordingly each time an action is performed.
     *
     * @param regexEntities    The list of regexes that the list keeps track of.
     * @param resetRegexSeeder default set of regexes when the list is cleared.
     */
    public RegexListPanel(String title,
                          String description,
                          List<RegexEntity> regexEntities,
                          Supplier<List<RegexEntity>> resetRegexSeeder) {
        this.regexEntities = regexEntities;
        this.panel = createRegexList(title, description, regexEntities, resetRegexSeeder);
    }

    public JPanel getPanel() {
        return panel;
    }

    /**
     * Set callback to be invoked when the regex list changes
     */
    public void setOnListChangedCallback(Runnable callback) {
        this.onListChangedCallback = callback;
    }

    /**
     * Notify listeners that the list has changed
     */
    private void notifyListChanged() {
        if (onListChangedCallback != null) {
            SwingUtilities.invokeLater(onListChangedCallback);
        }
    }

    /**
     * Refresh the table and counter display
     */
    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            if (tableModel != null) {
                tableModel.fireTableDataChanged();
            }
            if (counterLabel != null && regexEntities != null) {
                updateCounterLabel(counterLabel, regexEntities);
            }
            if (tabPaneOptions != null) {
                tabPaneOptions.validate();
                tabPaneOptions.repaint();
            }
        });
    }

    private JPanel createRegexList(String title,
                                   String description,
                                   List<RegexEntity> regexEntities,
                                   Supplier<List<RegexEntity>> resetRegexSeeder) {
        JPanel container;
        JPanel header;
        JPanel body;

        container = new JPanel();
        container.setLayout(new BorderLayout(0, 0));
        container.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));

        JLabel[] counterLabel = new JLabel[1]; // Array to hold reference
        header = createRegexListHeader(title, description, regexEntities, counterLabel);
        container.add(header, BorderLayout.NORTH);

        body = createRegexListBody(regexEntities, resetRegexSeeder, counterLabel[0]);
        container.add(body, BorderLayout.CENTER);

        return container;
    }

    private GridBagConstraints createGridConstraints(int gridx, int gridy, double weightx, double weighty, int anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbc.anchor = anchor;
        return gbc;
    }

    private GridBagConstraints createButtonGridConstraints(int gridx, int gridy) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(gridy == 0 ? 0 : 7, 7, 0, 0);
        gbc.ipadx = 35;
        gbc.ipady = 8;
        return gbc;
    }

    private JPanel createRegexListHeader(String title, String description, List<RegexEntity> regexEntities, JLabel[] counterLabelRef) {
        JPanel header;
        GridBagConstraints gbc;

        header = new JPanel();
        header.setLayout(new GridBagLayout());
        header.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));

        JLabel titleLabel = new JLabel();
        titleLabel.setFont(MainUI.UIOptions.H1_FONT);
        titleLabel.setForeground(MainUI.UIOptions.ACCENT_COLOR);
        titleLabel.setText(title);
        gbc = createGridConstraints(0, 0, 0.0, 1.0, GridBagConstraints.WEST);
        gbc.insets = new Insets(0, 0, 1, 0);
        header.add(titleLabel, gbc);

        JLabel subtitleLabel = new JLabel();
        subtitleLabel.setText(description);
        gbc = createGridConstraints(0, 1, 0.0, 1.0, GridBagConstraints.WEST);
        header.add(subtitleLabel, gbc);

        final JPanel spacer = new JPanel();
        gbc = createGridConstraints(1, 0, 1.0, 0.0, GridBagConstraints.CENTER);
        gbc.gridheight = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        header.add(spacer, gbc);

        // Counter label
        this.counterLabel = new JLabel();
        this.counterLabel.setFont(MainUI.UIOptions.H2_FONT);
        updateCounterLabel(this.counterLabel, regexEntities);
        gbc = createGridConstraints(2, 0, 0.0, 1.0, GridBagConstraints.EAST);
        gbc.gridheight = 2;
        gbc.insets = new Insets(0, 10, 0, 0);
        header.add(this.counterLabel, gbc);

        counterLabelRef[0] = this.counterLabel; // Store reference

        return header;
    }

    private void updateCounterLabel(JLabel counterLabel, List<RegexEntity> regexEntities) {
        long total = regexEntities.size();
        long active = regexEntities.stream().filter(RegexEntity::isActive).count();
        long inactive = total - active;
        counterLabel.setText(String.format("Total: %d | Active: %d | Inactive: %d", total, active, inactive));
    }

    private JPanel createRegexListBody(List<RegexEntity> regexEntities, Supplier<List<RegexEntity>> resetRegexSeeder, JLabel counterLabel) {
        JPanel container;
        JPanel containerRight;
        JPanel containerCenter;
        GridBagConstraints gbc;

        this.tableModel = new RegexListTableModel(regexEntities);
        this.tableModel.setOnListChangedCallback(() -> notifyListChanged());

        container = new JPanel(new BorderLayout(0, 0));
        this.tabPaneOptions = container; // Store reference for refresh
        containerCenter = new JPanel(new GridBagLayout());
        container.add(containerCenter, BorderLayout.CENTER);
        containerRight = new JPanel(new GridBagLayout());
        container.add(containerRight, BorderLayout.EAST);

        // table
        JTable regexTable = new RegexListTable(this.tableModel);
        RegexListTableModel tableModel = this.tableModel; // Local reference for convenience
        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(regexTable);
        gbc = createGridConstraints(0, 0, 1.0, 1.0, GridBagConstraints.CENTER);
        gbc.fill = GridBagConstraints.BOTH;
        containerCenter.add(scrollPane, gbc);

        // popup menu
        regexTable.addMouseListener(createRegexPopupMenu(regexEntities, regexTable, containerCenter, tableModel, counterLabel));

        // buttons
        JButton enableAllButton = createSetEnabledButton(regexEntities, true, containerCenter, tableModel, counterLabel);
        containerRight.add(enableAllButton, createButtonGridConstraints(0, 0));
        JButton disableAllButton = createSetEnabledButton(regexEntities, false, containerCenter, tableModel, counterLabel);
        containerRight.add(disableAllButton, createButtonGridConstraints(0, 1));

        JPopupMenu listMenu = new JPopupMenu();
        listMenu.add(createListClearMenuItem(regexEntities, containerCenter, tableModel, counterLabel));
        listMenu.add(createListResetMenuItem(regexEntities, resetRegexSeeder, containerCenter, tableModel, counterLabel));
        listMenu.add(createListOpenMenuItem(regexEntities, containerCenter, tableModel, counterLabel));
        listMenu.add(createListSaveMenuItem(regexEntities));
        JToggleButton listButton = new PopupMenuButton(getLocaleString("options-list-listSubmenu"), listMenu);
        containerRight.add(listButton, createButtonGridConstraints(0, 2));

        JPopupMenu regexMenu = new JPopupMenu();
        regexMenu.add(createNewRegexMenuItem(regexEntities, containerCenter, tableModel, counterLabel));
        regexMenu.add(createEditRegexMenuItem(regexEntities, regexTable, containerCenter, tableModel, counterLabel));
        regexMenu.add(createDeleteRegexMenuItem(regexEntities, regexTable, containerCenter, tableModel, counterLabel));
        JToggleButton regexButton = new PopupMenuButton(getLocaleString("options-list-regexSubmenu"), regexMenu);
        containerRight.add(regexButton, createButtonGridConstraints(0, 3));

        return container;
    }

    /**
     * Create a popup menu over the selected regexTable entry and show edit and delete buttons.
     *
     * @param regexEntities
     * @param regexTable
     * @param tabPaneOptions
     * @param tableModel
     */
    private MouseListener createRegexPopupMenu(List<RegexEntity> regexEntities, JTable regexTable, JPanel tabPaneOptions, RegexListTableModel tableModel, JLabel counterLabel) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onMouseEvent(e);
            }

            private void onMouseEvent(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = regexTable.getSelectedRow();
                    if (row == -1) return;
                    if (e.getComponent() instanceof JTable) {
                        JPopupMenu regexMenu = new JPopupMenu();
                        regexMenu.add(new JMenuItem(new AbstractAction(getLocaleString("options-list-edit")) {
                            @Override
                            public void actionPerformed(final ActionEvent e) {
                                editSelectedRegex(regexEntities, regexTable, tabPaneOptions, tableModel, counterLabel);
                            }
                        }));
                        regexMenu.add(new JMenuItem(new AbstractAction(getLocaleString("options-list-delete")) {
                            @Override
                            public void actionPerformed(final ActionEvent e) {
                                deleteSelectedRegex(regexEntities, regexTable, tabPaneOptions, tableModel, counterLabel);
                            }
                        }));
                        regexMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        };
    }

    private JMenuItem createEditRegexMenuItem(List<RegexEntity> regexEntities,
                                              JTable optionsRegexTable,
                                              JPanel tabPaneOptions,
                                              RegexListTableModel tableModel,
                                              JLabel counterLabel) {
        JMenuItem btnEditRegex = new JMenuItem(getLocaleString("options-list-edit"));
        btnEditRegex.setEnabled(false);
        btnEditRegex.addActionListener(actionEvent -> {
            editSelectedRegex(regexEntities, optionsRegexTable, tabPaneOptions, tableModel, counterLabel);
        });
        optionsRegexTable.getSelectionModel().addListSelectionListener(event -> {
            int viewRow = optionsRegexTable.getSelectedRow();
            btnEditRegex.setEnabled(!event.getValueIsAdjusting() && viewRow >= 0);
        });
        return btnEditRegex;
    }

    /**
     * Open the edit regex dialog of the selected regex.
     *
     * @param regexEntities
     * @param optionsRegexTable
     * @param tabPaneOptions
     * @param tableModel
     */
    private void editSelectedRegex(List<RegexEntity> regexEntities,
                                   JTable optionsRegexTable,
                                   JPanel tabPaneOptions,
                                   RegexListTableModel tableModel,
                                   JLabel counterLabel) {
        int rowIndex;
        int realRow;

        rowIndex = optionsRegexTable.getSelectedRow();
        if (rowIndex == -1) return;
        realRow = optionsRegexTable.convertRowIndexToModel(rowIndex);

        RegexEntity previousRegex = regexEntities.get(realRow);
        RegexEditDialog dialog = new RegexEditDialog(previousRegex);
        if (!dialog.showDialog(tabPaneOptions, getLocaleString("options-list-edit-dialogTitle"))) return;

        RegexEntity newRegex = dialog.getRegexEntity();
        if (newRegex.getRegex().isEmpty() && newRegex.getDescription().isEmpty()) return;
        if (previousRegex.equals(newRegex)) return;

        regexEntities.set(realRow, newRegex);

        SwingUtilities.invokeLater(() -> {
            // Use fireTableDataChanged instead of fireTableRowsUpdated to avoid
            // IndexOutOfBoundsException with RowSorter
            tableModel.fireTableDataChanged();
            updateCounterLabel(counterLabel, regexEntities);
            tabPaneOptions.validate();
            tabPaneOptions.repaint();
            notifyListChanged();
        });
    }

    private JMenuItem createDeleteRegexMenuItem(List<RegexEntity> regexEntities,
                                                JTable optionsRegexTable,
                                                JPanel tabPaneOptions,
                                                RegexListTableModel tableModel,
                                                JLabel counterLabel) {
        JMenuItem btnDeleteRegex = new JMenuItem(getLocaleString("options-list-delete"));
        btnDeleteRegex.setEnabled(false);
        btnDeleteRegex.addActionListener(actionEvent -> {
            deleteSelectedRegex(regexEntities, optionsRegexTable, tabPaneOptions, tableModel, counterLabel);
        });
        optionsRegexTable.getSelectionModel().addListSelectionListener(event -> {
            int viewRow = optionsRegexTable.getSelectedRow();
            btnDeleteRegex.setEnabled(!event.getValueIsAdjusting() && viewRow >= 0);
        });
        return btnDeleteRegex;
    }

    /**
     * Delete the selected regex from the table.
     *
     * @param regexEntities
     * @param optionsRegexTable
     * @param tabPaneOptions
     * @param tableModel
     */
    private void deleteSelectedRegex(List<RegexEntity> regexEntities,
                                     JTable optionsRegexTable,
                                     JPanel tabPaneOptions,
                                     RegexListTableModel tableModel,
                                     JLabel counterLabel) {
        int rowIndex = optionsRegexTable.getSelectedRow();
        if (rowIndex == -1) return;
        int realRow = optionsRegexTable.convertRowIndexToModel(rowIndex);
        regexEntities.remove(realRow);

        SwingUtilities.invokeLater(() -> {
            // Use fireTableDataChanged instead of fireTableRowsDeleted to avoid
            // IndexOutOfBoundsException with RowSorter
            tableModel.fireTableDataChanged();
            updateCounterLabel(counterLabel, regexEntities);
            tabPaneOptions.validate();
            tabPaneOptions.repaint();
            notifyListChanged();
        });
    }

    private JMenuItem createNewRegexMenuItem(List<RegexEntity> regexEntities,
                                             JPanel tabPaneOptions,
                                             RegexListTableModel tableModel,
                                             JLabel counterLabel) {
        JMenuItem btnNewRegex = new JMenuItem(getLocaleString("options-list-new"));
        btnNewRegex.addActionListener(actionEvent -> {
            RegexEditDialog dialog = new RegexEditDialog();
            if (!dialog.showDialog(tabPaneOptions, getLocaleString("options-list-new-dialogTitle"))) return;

            RegexEntity newRegex = dialog.getRegexEntity();
            if (newRegex.getRegex().isEmpty() && newRegex.getDescription().isEmpty()) return;

            int row = regexEntities.size();
            regexEntities.add(newRegex);
            tableModel.fireTableRowsInserted(row, row);
            updateCounterLabel(counterLabel, regexEntities);
            tabPaneOptions.validate();
            tabPaneOptions.repaint();
            notifyListChanged();
        });
        return btnNewRegex;
    }

    private JMenuItem createListSaveMenuItem(List<RegexEntity> regexEntities) {
        JMenuItem menuItem = new JMenuItem(getLocaleString("options-list-save"));
        List<String> options = Arrays.asList("JSON", "CSV");
        menuItem.addActionListener(actionEvent -> {
            String filepath = SwingUtils.selectFile(options, false);
            FileUtils.exportRegexListToFile(filepath, regexEntities);
        });

        return menuItem;
    }

    private JMenuItem createListOpenMenuItem(List<RegexEntity> regexEntities,
                                             JPanel tabPaneOptions,
                                             RegexListTableModel tableModel,
                                             JLabel counterLabel) {
        List<String> options = Arrays.asList("JSON", "CSV");
        JMenuItem menuItem = new JMenuItem(getLocaleString("options-list-open"));
        menuItem.addActionListener(actionEvent -> {
            StringBuilder message = new StringBuilder();
            String filepath = SwingUtils.selectFile(options, true);

            try {
                FileUtils.importRegexListFromFile(filepath, regexEntities)
                        .stream()
                        .map(entity -> String.format("%s - %s\n", entity.getDescription(), entity.getRegex()))
                        .forEach(message::append);
                SwingUtilities.invokeLater(() -> SwingUtils.showMessageDialog(
                        getLocaleString("options-list-open-alreadyPresentTitle"),
                        getLocaleString("options-list-open-alreadyPresentWarn"),
                        message.toString()));
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> SwingUtils.showMessageDialog(
                        getLocaleString("options-list-open-importErrorTitle"),
                        getLocaleString("options-list-open-importErrorWarn"),
                        exception.toString()));
            }

            tableModel.fireTableDataChanged();
            updateCounterLabel(counterLabel, regexEntities);
            tabPaneOptions.validate();
            tabPaneOptions.repaint();
            notifyListChanged();
        });

        return menuItem;
    }

    private JMenuItem createListClearMenuItem(List<RegexEntity> regexEntities,
                                              JPanel tabPaneOptions,
                                              RegexListTableModel tableModel,
                                              JLabel counterLabel) {
        JMenuItem btnClearRegex = new JMenuItem(getLocaleString("options-list-clear"));
        btnClearRegex.addActionListener(actionEvent -> {
            int dialogRes = JOptionPane.showConfirmDialog(
                    null,
                    getLocaleString("options-list-clear-message"),
                    getLocaleString("options-list-clear-title"),
                    JOptionPane.YES_NO_OPTION);
            if (dialogRes != JOptionPane.OK_OPTION) return;

            if (!regexEntities.isEmpty()) {
                regexEntities.subList(0, regexEntities.size()).clear();
                tableModel.fireTableDataChanged();
                updateCounterLabel(counterLabel, regexEntities);
                tabPaneOptions.validate();
                tabPaneOptions.repaint();
                notifyListChanged();
            }
        });
        return btnClearRegex;
    }

    private JMenuItem createListResetMenuItem(List<RegexEntity> regexEntities,
                                              Supplier<List<RegexEntity>> resetRegexSeeder,
                                              JPanel tabPaneOptions,
                                              RegexListTableModel tableModel,
                                              JLabel counterLabel) {
        JMenuItem btnResetRegex = new JMenuItem(getLocaleString("options-list-reset"));
        btnResetRegex.addActionListener(actionEvent -> {
            int dialogRes = JOptionPane.showConfirmDialog(
                    null,
                    getLocaleString("options-list-reset-message"),
                    getLocaleString("options-list-reset-title"),
                    JOptionPane.YES_NO_OPTION);
            if (dialogRes != JOptionPane.OK_OPTION) return;

            if (!regexEntities.isEmpty()) {
                regexEntities.subList(0, regexEntities.size()).clear();
            }

            regexEntities.clear();
            regexEntities.addAll(resetRegexSeeder.get());

            tableModel.fireTableDataChanged();
            updateCounterLabel(counterLabel, regexEntities);
            tabPaneOptions.validate();
            tabPaneOptions.repaint();
            notifyListChanged();
        });
        return btnResetRegex;
    }

    private JButton createSetEnabledButton(List<RegexEntity> regexEntities,
                                           boolean isEnabled,
                                           JPanel tabPaneOptions,
                                           RegexListTableModel tableModel,
                                           JLabel counterLabel) {
        String label = getLocaleString(isEnabled ? "options-list-enableAll" : "options-list-disableAll");
        JButton btnSetAllEnabled = new JButton(label);
        btnSetAllEnabled.addActionListener(actionEvent -> {
            regexEntities.forEach(regex -> regex.setActive(isEnabled));

            tableModel.fireTableDataChanged();
            updateCounterLabel(counterLabel, regexEntities);
            tabPaneOptions.validate();
            tabPaneOptions.repaint();
            notifyListChanged();
        });
        return btnSetAllEnabled;
    }
}
