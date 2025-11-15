package com.tzm.supafinder.model;

import java.util.List;

/**
 * Adapter model for deserializing a list of regexes from a JSON file
 */
public class RegexEntityJsonAdapter {
    private boolean active;
    private String regex;
    private String refinerRegex;
    private String description;
    private List<String> sections;
    private List<String> tests;
    private Integer importance; // 0-5, nullable (defaults to 2 if not specified)

    public RegexEntityJsonAdapter() {
    }

    public boolean isActive() {
        return active;
    }

    public String getRegex() {
        return regex;
    }

    public String getRefinerRegex() {
        return refinerRegex;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getSections() {
        return sections;
    }

    public List<String> getTests() {
        return tests;
    }

    public Integer getImportance() {
        return importance != null ? importance : 2; // Default to 2 (Low) if not specified
    }
}
