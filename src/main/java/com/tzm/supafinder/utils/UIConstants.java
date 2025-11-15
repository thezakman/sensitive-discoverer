package com.tzm.supafinder.utils;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/**
 * Centralized UI constants for consistent spacing and sizing
 */
public class UIConstants {

    // Button dimensions
    public static final Dimension BUTTON_STANDARD = new Dimension(-1, 45);
    public static final Dimension BUTTON_SMALL = new Dimension(80, 25);
    public static final Dimension SPINNER_STANDARD = new Dimension(80, 25);
    public static final Dimension STATS_BOX = new Dimension(110, 30);

    // Spacing
    public static final Insets INSETS_NONE = new Insets(0, 0, 0, 0);
    public static final Insets INSETS_SMALL = new Insets(2, 2, 2, 2);
    public static final Insets INSETS_MEDIUM = new Insets(5, 5, 5, 5);
    public static final Insets INSETS_LARGE = new Insets(10, 10, 10, 10);

    // Table column widths
    public static final int COL_WIDTH_IMPORTANCE_MIN = 70;
    public static final int COL_WIDTH_IMPORTANCE_MAX = 100;
    public static final int COL_WIDTH_IMPORTANCE_PREF = 85;

    public static final int COL_WIDTH_REGEX_MIN = 150;
    public static final int COL_WIDTH_REGEX_PREF = 250;

    public static final int COL_WIDTH_MATCH_MIN = 100;
    public static final int COL_WIDTH_MATCH_PREF = 200;

    public static final int COL_WIDTH_URL_MIN = 200;
    public static final int COL_WIDTH_URL_PREF = 350;

    public static final int COL_WIDTH_SECTION_MIN = 80;
    public static final int COL_WIDTH_SECTION_MAX = 120;
    public static final int COL_WIDTH_SECTION_PREF = 100;

    // Font sizes
    public static final int FONT_SIZE_TITLE = 36;
    public static final int FONT_SIZE_SUBTITLE = 24;
    public static final int FONT_SIZE_BODY = 18;
    public static final int FONT_SIZE_H1 = 16;
    public static final int FONT_SIZE_H2 = 14;
    public static final int FONT_SIZE_SMALL = 12;
    public static final int FONT_SIZE_TINY = 11;

    // Scan limits
    public static final int HISTORY_SCAN_LIMIT_MIN = -1;
    public static final int HISTORY_SCAN_LIMIT_MAX = 100000;
    public static final int HISTORY_SCAN_LIMIT_STEP = 10;
    public static final int HISTORY_SCAN_LIMIT_DEFAULT = -1;

    // Font styles
    public static final Font FONT_DIALOG_BOLD_12 = new Font("Dialog", Font.BOLD, 12);
    public static final Font FONT_DIALOG_BOLD_11 = new Font("Dialog", Font.BOLD, 11);
}
