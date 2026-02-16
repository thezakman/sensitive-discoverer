package com.tzm.supafinder.utils;

import com.tzm.supafinder.model.HttpSection;
import com.tzm.supafinder.model.RegexEntity;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Parser for YAML-based regex patterns
 */
public class YamlParser {

    /**
     * Parse a single YAML file into a RegexEntity
     * @param yamlFile The YAML file to parse
     * @return RegexEntity created from the YAML file
     * @throws IOException if file cannot be read
     */
    public static RegexEntity parseYamlFile(File yamlFile) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = new FileInputStream(yamlFile)) {
            Map<String, Object> data = yaml.load(inputStream);
            return convertMapToRegexEntity(data);
        }
    }

    /**
     * Parse a single YAML file path into a RegexEntity
     * @param yamlPath The path to the YAML file
     * @return RegexEntity created from the YAML file
     * @throws IOException if file cannot be read
     */
    public static RegexEntity parseYamlFile(Path yamlPath) throws IOException {
        return parseYamlFile(yamlPath.toFile());
    }

    /**
     * Recursively parse all YAML files in a directory
     * @param directory Directory containing YAML files
     * @return List of RegexEntity objects from all YAML files
     * @throws IOException if directory cannot be read
     */
    public static List<RegexEntity> parseYamlDirectory(File directory) throws IOException {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Path must be a directory: " + directory.getAbsolutePath());
        }

        List<RegexEntity> entities = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase().endsWith(".yaml") ||
                               path.toString().toLowerCase().endsWith(".yml"))
                .forEach(path -> {
                    try {
                        RegexEntity entity = parseYamlFile(path);
                        entities.add(entity);
                    } catch (Exception e) {
                        // Log the error but continue processing other files
                        System.err.println("Error parsing YAML file " + path + ": " + e.getMessage());
                    }
                });
        }

        return entities;
    }

    /**
     * Convert a YAML map to a RegexEntity
     */
    private static RegexEntity convertMapToRegexEntity(Map<String, Object> data) {
        // Extract basic fields
        String name = (String) data.get("name");
        String category = (String) data.get("category");

        // Extract tags
        List<String> tags = extractStringList(data.get("tags"));

        // Extract importance (default to 2/low if not specified)
        int importance = data.containsKey("importance") ?
            ((Number) data.get("importance")).intValue() : 2;

        // Extract precheck fields
        boolean precheckNeeded = extractBoolean(data, "precheck_needed", false);
        List<String> prechecks = extractStringList(data.get("prechecks"));

        // Extract case_insensitive
        boolean caseInsensitive = extractBoolean(data, "case_insensitive", true);

        // Extract stop_first_occurrence
        boolean stopFirstOccurrence = extractBoolean(data, "stop_first_occurrence", false);

        // Extract regexes
        List<String> regexes = extractStringList(data.get("regexes"));
        if (regexes == null || regexes.isEmpty()) {
            throw new IllegalArgumentException("YAML must contain at least one regex pattern");
        }

        // Extract ignore patterns
        List<String> ignorePatterns = extractStringList(data.get("ignore"));

        // For multiple regexes, combine them with OR operator
        String combinedRegex = regexes.size() == 1 ?
            regexes.get(0) :
            regexes.stream()
                .map(r -> "(?:" + r + ")")
                .reduce((a, b) -> a + "|" + b)
                .orElse(regexes.get(0));

        // Default to response body section (most common for sensitive data)
        EnumSet<HttpSection> sections = HttpSection.getDefault();

        // Build description from name and category
        String description = name + (category != null ? " [" + category + "]" : "");

        return new RegexEntity(
            description,
            combinedRegex,
            true, // active by default
            sections,
            null, // no refiner regex
            null, // no tests
            category,
            tags,
            importance,
            precheckNeeded,
            prechecks,
            caseInsensitive,
            stopFirstOccurrence,
            ignorePatterns
        );
    }

    /**
     * Helper to extract a list of strings from YAML data
     */
    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }

        // If it's a single string, wrap it in a list
        return Collections.singletonList(obj.toString());
    }

    /**
     * Safely extract a boolean value from YAML data, handling String to Boolean conversion
     *
     * @param data Map containing YAML data
     * @param key Key to extract
     * @param defaultValue Default value if key is missing
     * @return Boolean value
     */
    private static boolean extractBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        if (!data.containsKey(key)) {
            return defaultValue;
        }

        Object value = data.get(key);
        if (value == null) {
            return defaultValue;
        }

        // Handle actual Boolean
        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        // Handle String representation ("true", "false", "yes", "no", etc.)
        if (value instanceof String) {
            String strValue = ((String) value).toLowerCase().trim();
            return strValue.equals("true") || strValue.equals("yes") || strValue.equals("1");
        }

        // Handle Number (0 = false, non-zero = true)
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }

        return defaultValue;
    }
}
