package com.tzm.supafinder.utils;

/**
 * Utility class for importance level operations
 */
public class ImportanceUtils {

    /**
     * Convert importance level (0-5) to human-readable label
     *
     * @param importance The importance level (0-5)
     * @return Human-readable label
     */
    public static String getImportanceLabel(int importance) {
        return switch (importance) {
            case 0 -> "Debug";
            case 1 -> "Info";
            case 2 -> "Low";
            case 3 -> "Medium";
            case 4 -> "High";
            case 5 -> "Critical";
            default -> "Unknown";
        };
    }

    /**
     * Get importance level with numeric suffix
     *
     * @param importance The importance level (0-5)
     * @return Label with number, e.g. "Debug (0)"
     */
    public static String getImportanceLabelWithNumber(int importance) {
        return getImportanceLabel(importance) + " (" + importance + ")";
    }

    /**
     * Get all importance level labels
     *
     * @return Array of all labels
     */
    public static String[] getAllLabels() {
        return new String[]{"Debug", "Info", "Low", "Medium", "High", "Critical"};
    }

    /**
     * Get all importance level labels with numbers
     *
     * @return Array of all labels with numbers
     */
    public static String[] getAllLabelsWithNumbers() {
        return new String[]{
            "Debug (0)",
            "Info (1)",
            "Low (2)",
            "Medium (3)",
            "High (4)",
            "Critical (5)"
        };
    }
}
