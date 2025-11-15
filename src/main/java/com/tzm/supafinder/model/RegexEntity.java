package com.tzm.supafinder.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static com.tzm.supafinder.utils.Messages.getLocaleString;

/**
 * An entity for a regex that can be used in scans.
 * Once create this entity is immutable, and can only be activated/deactivated;
 */
public class RegexEntity {
    private final String regex;
    private final transient Pattern regexCompiled;
    private final String refinerRegex;
    private final transient Pattern refinerRegexCompiled;
    private final String description;
    private final EnumSet<HttpSection> sections;
    private final List<String> tests;
    private boolean active;

    // YAML-specific fields
    private final String category;
    private final List<String> tags;
    private final int importance; // 0=debug, 1=info, 2=low, 3=medium, 4=high, 5=critical
    private final boolean precheckNeeded;
    private final List<String> prechecks;
    private final boolean caseInsensitive;
    private final boolean stopFirstOccurrence;
    private final List<String> ignorePatterns;
    private final transient List<Pattern> ignoreCompiledPatterns;

    public RegexEntity(String description, String regex) throws IllegalArgumentException {
        this(description, regex, true, HttpSection.getDefault(), null, null, null, null, 2, false, null, false, false, null);
    }

    public RegexEntity(String description, String regex, boolean active) throws IllegalArgumentException {
        this(description, regex, active, HttpSection.getDefault(), null, null, null, null, 2, false, null, false, false, null);
    }

    public RegexEntity(String description, String regex, boolean active, EnumSet<HttpSection> sections, String refinerRegex) throws IllegalArgumentException {
        this(description, regex, active, sections, refinerRegex, null, null, null, 2, false, null, false, false, null);
    }

    /**
     * @param description
     * @param regex
     * @param active
     * @param sections
     * @param refinerRegex regex to refine the match. It is used only after the main regex matches, and it's applied to
     *                     a defined range before the match. This regex always ends with a "$" (dollar sign) to ensure
     *                     the result can be prepended to the match. If the final "$" is missing, it's added automatically.
     * @param tests
     */
    public RegexEntity(String description, String regex, boolean active, EnumSet<HttpSection> sections, String refinerRegex, List<String> tests) throws IllegalArgumentException {
        this(description, regex, active, sections, refinerRegex, tests, null, null, 2, false, null, false, false, null);
    }

    /**
     * Full constructor with YAML support
     */
    public RegexEntity(String description, String regex, boolean active, EnumSet<HttpSection> sections,
                      String refinerRegex, List<String> tests, String category, List<String> tags,
                      int importance, boolean precheckNeeded, List<String> prechecks,
                      boolean caseInsensitive, boolean stopFirstOccurrence, List<String> ignorePatterns) throws IllegalArgumentException {
        if (regex == null || regex.isBlank()) {
            throw new IllegalArgumentException(getLocaleString("exception-invalidRegex"));
        }
        if (sections == null) {
            throw new IllegalArgumentException(getLocaleString("exception-invalidSections"));
        }

        this.active = active;
        this.description = description;
        this.regex = regex;

        // Compile regex with case sensitivity option
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        this.regexCompiled = Pattern.compile(regex, flags);

        if (Objects.isNull(refinerRegex) || refinerRegex.isBlank()) {
            this.refinerRegex = null;
            this.refinerRegexCompiled = null;
        } else {
            this.refinerRegex = refinerRegex.endsWith("$") ? refinerRegex : refinerRegex + "$";
            this.refinerRegexCompiled = Pattern.compile(this.refinerRegex, flags);
        }
        this.sections = sections;
        this.tests = tests;

        // YAML fields
        this.category = category;
        this.tags = tags;
        this.importance = importance;
        this.precheckNeeded = precheckNeeded;
        this.prechecks = prechecks;
        this.caseInsensitive = caseInsensitive;
        this.stopFirstOccurrence = stopFirstOccurrence;
        this.ignorePatterns = ignorePatterns;

        // Compile ignore patterns
        if (ignorePatterns != null && !ignorePatterns.isEmpty()) {
            this.ignoreCompiledPatterns = ignorePatterns.stream()
                .map(pattern -> Pattern.compile(pattern, flags))
                .toList();
        } else {
            this.ignoreCompiledPatterns = null;
        }
    }

    public RegexEntity(RegexEntity entity) throws IllegalArgumentException {
        this(entity.getDescription(), entity.getRegex(), entity.isActive(), entity.getSections(),
            entity.getRefinerRegex().orElse(null), entity.getTests(), entity.getCategory(),
            entity.getTags(), entity.getImportance(), entity.isPrecheckNeeded(),
            entity.getPrechecks(), entity.isCaseInsensitive(), entity.isStopFirstOccurrence(),
            entity.getIgnorePatterns());
    }

    /**
     * Tries to match the CSV line as a RegexEntity.
     * <br><br>
     * There are 2 supported formats:
     * <ul><li>the extended: {@code "...","...","...","..."}</li><li>the simple: {@code "...","..."}</li></ul>
     *
     * @param input CSV line to match against one of the formats
     * @return An Optional that may contain the successful result of the match. When there's a result, it always has 4 groups but the 3rd and 4th are null when the simple format is matched.
     */
    public static Optional<MatchResult> checkRegexEntityFromCSV(String input) {
        return Pattern
                .compile("^\"(.+?)\",\"(.+?)\"(?:,\"(.*?)\",\"(.*?)\")?$")
                .matcher(input)
                .results()
                .findFirst();
    }

    public List<String> getTests() {
        return tests;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean value) {
        this.active = value;
    }

    public String getRegex() {
        return this.regex;
    }

    public Pattern getRegexCompiled() {
        return this.regexCompiled;
    }

    public Optional<String> getRefinerRegex() {
        return Optional.ofNullable(refinerRegex);
    }

    public Optional<Pattern> getRefinerRegexCompiled() {
        return Optional.ofNullable(refinerRegexCompiled);
    }

    public String getDescription() {
        return this.description;
    }

    public EnumSet<HttpSection> getSections() {
        return sections;
    }

    public String getSectionsHumanReadable() {
        String reqSections = sections.toString()
                .replaceAll("Request", "")
                .replaceAll("Response\\w+(, )?", "")
                .replaceAll("(\\[]|, (?=]))", "");
        if (!reqSections.isBlank()) reqSections = "REQ" + reqSections;
        String resSections = sections.toString()
                .replaceAll("Response", "")
                .replaceAll("Request\\w+(, )?", "")
                .replaceAll("(\\[]|, (?=]))", "");
        if (!resSections.isBlank()) resSections = "RES" + resSections;
        String separator = (reqSections.isBlank() || resSections.isBlank()) ? "" : ", ";
        return String.format("%s%s%s", reqSections, separator, resSections);
    }

    // YAML field getters
    public String getCategory() {
        return category;
    }

    public List<String> getTags() {
        return tags;
    }

    public int getImportance() {
        return importance;
    }

    public boolean isPrecheckNeeded() {
        return precheckNeeded;
    }

    public List<String> getPrechecks() {
        return prechecks;
    }

    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    public boolean isStopFirstOccurrence() {
        return stopFirstOccurrence;
    }

    public List<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    public List<Pattern> getIgnoreCompiledPatterns() {
        return ignoreCompiledPatterns;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegexEntity entity = (RegexEntity) o;
        return Objects.equals(getRegex(), entity.getRegex()) &&
                Objects.equals(getRefinerRegex(), entity.getRefinerRegex()) &&
                Objects.equals(getDescription(), entity.getDescription()) &&
                Objects.equals(getSections(), entity.getSections());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRegex(), getRefinerRegex(), getDescription(), getSections());
    }

    @Override
    public String toString() {
        return "RegexEntity{" +
                "regex='" + getRegex() + '\'' +
                ", refinerRegex='" + getRefinerRegex().orElse("") + '\'' +
                ", description='" + getDescription() + '\'' +
                ", sections=" + getSectionsHumanReadable() +
                '}';
    }
}
