package com.tzm.supafinder.utils;

import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Factory class for creating GridBagConstraints with common patterns
 */
public class GridConstraintsFactory {

    /**
     * Create basic GridBagConstraints
     *
     * @param gridx   Grid column
     * @param gridy   Grid row
     * @param weightx Horizontal weight
     * @param weighty Vertical weight
     * @param anchor  Anchor position
     * @return Configured GridBagConstraints
     */
    public static GridBagConstraints create(int gridx, int gridy, double weightx, double weighty, int anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbc.anchor = anchor;
        return gbc;
    }

    /**
     * Create GridBagConstraints with fill
     *
     * @param gridx   Grid column
     * @param gridy   Grid row
     * @param weightx Horizontal weight
     * @param weighty Vertical weight
     * @param fill    Fill direction
     * @return Configured GridBagConstraints
     */
    public static GridBagConstraints createWithFill(int gridx, int gridy, double weightx, double weighty, int fill) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbc.fill = fill;
        return gbc;
    }

    /**
     * Create GridBagConstraints for buttons
     *
     * @param gridx Grid column
     * @param gridy Grid row
     * @return Configured GridBagConstraints for buttons
     */
    public static GridBagConstraints createForButton(int gridx, int gridy) {
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

    /**
     * Create GridBagConstraints with custom insets
     *
     * @param gridx   Grid column
     * @param gridy   Grid row
     * @param weightx Horizontal weight
     * @param weighty Vertical weight
     * @param fill    Fill direction
     * @param insets  Custom insets
     * @return Configured GridBagConstraints
     */
    public static GridBagConstraints createWithInsets(int gridx, int gridy, double weightx, double weighty, int fill, Insets insets) {
        GridBagConstraints gbc = createWithFill(gridx, gridy, weightx, weighty, fill);
        gbc.insets = insets;
        return gbc;
    }
}
