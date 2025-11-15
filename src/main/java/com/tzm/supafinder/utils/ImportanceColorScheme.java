package com.tzm.supafinder.utils;

import java.awt.Color;

/**
 * Centralized color scheme for importance levels
 */
public class ImportanceColorScheme {

    // Importance level colors
    public static final Color DEBUG_COLOR = new Color(220, 220, 220);    // Gray
    public static final Color INFO_COLOR = new Color(200, 230, 255);     // Light blue
    public static final Color LOW_COLOR = new Color(255, 255, 200);      // Light yellow
    public static final Color MEDIUM_COLOR = new Color(255, 220, 150);   // Orange
    public static final Color HIGH_COLOR = new Color(255, 180, 150);     // Light red-orange
    public static final Color CRITICAL_COLOR = new Color(255, 150, 150); // Light red

    /**
     * Get color for given importance level
     *
     * @param importance The importance level (0-5)
     * @return Color for that importance level
     */
    public static Color getColor(int importance) {
        return switch (importance) {
            case 0 -> DEBUG_COLOR;
            case 1 -> INFO_COLOR;
            case 2 -> LOW_COLOR;
            case 3 -> MEDIUM_COLOR;
            case 4 -> HIGH_COLOR;
            case 5 -> CRITICAL_COLOR;
            default -> Color.WHITE;
        };
    }

    /**
     * Get all colors as array
     *
     * @return Array of all importance colors
     */
    public static Color[] getAllColors() {
        return new Color[]{
            DEBUG_COLOR,
            INFO_COLOR,
            LOW_COLOR,
            MEDIUM_COLOR,
            HIGH_COLOR,
            CRITICAL_COLOR
        };
    }
}
