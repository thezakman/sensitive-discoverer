package com.tzm.supafinder.model;

import burp.api.montoya.persistence.Preferences;
import com.tzm.supafinder.RegexSeeder;
import com.tzm.supafinder.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import static com.tzm.supafinder.utils.Messages.getLocaleString;

/**
 * Options used by the RegexScanner for modifying its behaviour
 */
public class RegexScannerOptions {
    private final Properties configProperties;
    private final Preferences burpPreferences;
    private final List<RegexEntity> generalRegexList;
    private final List<RegexEntity> extensionsRegexList;
    private final List<Runnable> importanceChangeListeners;

    /**
     * Checkbox to skip responses not in scope
     */
    private boolean filterInScopeCheckbox;
    /**
     * Checkbox to skip responses over a set max size
     */
    private boolean filterSkipMaxSizeCheckbox;
    /**
     * Checkbox to skip responses of a media MIME-type
     */
    private boolean filterSkipMediaTypeCheckbox;

    /**
     * Max response size in bytes
     */
    private int configMaxResponseSize;
    /**
     * Number of threads to use to scan items
     */
    private int configNumberOfThreads;
    /**
     * The size, in bytes, of the region before the match where the refinerRegex is applied
     */
    private int configRefineContextSize;

    // New real-time analysis options
    /**
     * Enable real-time analysis of HTTP traffic
     */
    private boolean realtimeAnalysisEnabled;

    /**
     * History scan limit (number of requests to scan, -1 for all)
     */
    private int historyScanLimit;

    /**
     * Tool sources to analyze (comma-separated: ALL, TARGET, PROXY, INTRUDER, REPEATER, SCANNER, SEQUENCER, EXTENSIONS)
     */
    private String toolSources;

    /**
     * Origin filter (REQUESTS, RESPONSES, BOTH)
     */
    private String originFilter;

    /**
     * Selected importance levels (can be multiple: 0=Debug, 1=Info, 2=Low, 3=Medium, 4=High, 5=Critical)
     */
    private java.util.Set<Integer> selectedImportanceLevels;

    /**
     * YAML auto-watch directory path
     */
    private String yamlAutoWatchPath;

    /**
     * Enable YAML auto-watch
     */
    private boolean yamlAutoWatchEnabled;

    /**
     * @param configProperties The default options to load if persistent preferences are missing.
     * @param burpPreferences  The instance for accessing Burp Suite's functionality for persisting preferences.
     */
    public RegexScannerOptions(Properties configProperties, Preferences burpPreferences) {
        if (Objects.isNull(configProperties))
            throw new IllegalArgumentException(getLocaleString("exception-invalidProperties"));
        if (Objects.isNull(burpPreferences))
            throw new IllegalArgumentException(getLocaleString("exception-invalidPreferences"));

        this.configProperties = configProperties;
        this.burpPreferences = burpPreferences;
        this.generalRegexList = new ArrayList<>();
        this.extensionsRegexList = new ArrayList<>();
        this.importanceChangeListeners = new ArrayList<>();

        loadOptionsDefaults();
        loadOptionsPersisted();
        loadRegexes(false);
    }

    private void loadOptionsDefaults() {
        this.setFilterInScopeCheckbox(Boolean.parseBoolean(configProperties.getProperty("config.scanner.filter.in_scope")));
        this.setFilterSkipMaxSizeCheckbox(Boolean.parseBoolean(configProperties.getProperty("config.scanner.filter.skip_max_size")));
        this.setFilterSkipMediaTypeCheckbox(Boolean.parseBoolean(configProperties.getProperty("config.scanner.filter.skip_media_type")));
        this.setConfigMaxResponseSize(Integer.parseInt(configProperties.getProperty("config.scanner.max_response_size")));
        this.setConfigNumberOfThreads(Integer.parseInt(configProperties.getProperty("config.scanner.number_of_threads")));
        this.setConfigRefineContextSize(Integer.parseInt(configProperties.getProperty("config.scanner.refine_context_size")));

        // New options - set defaults
        this.setRealtimeAnalysisEnabled(false);
        this.setHistoryScanLimit(-1); // -1 means all
        this.setToolSources("PROXY");
        this.setOriginFilter("BOTH");
        // By default, select all importance levels
        this.selectedImportanceLevels = new java.util.HashSet<>();
        for (int i = 0; i <= 5; i++) {
            this.selectedImportanceLevels.add(i);
        }
        this.setYamlAutoWatchPath("");
        this.setYamlAutoWatchEnabled(false);
    }

    private void loadOptionsPersisted() {
        this.setFilterInScopeCheckbox(burpPreferences.getBoolean("config.scanner.filter.in_scope"));
        this.setFilterSkipMaxSizeCheckbox(burpPreferences.getBoolean("config.scanner.filter.skip_max_size"));
        this.setFilterSkipMediaTypeCheckbox(burpPreferences.getBoolean("config.scanner.filter.skip_media_type"));
        this.setConfigMaxResponseSize(burpPreferences.getInteger("config.scanner.max_response_size"));
        this.setConfigNumberOfThreads(burpPreferences.getInteger("config.scanner.number_of_threads"));
        this.setConfigRefineContextSize(burpPreferences.getInteger("config.scanner.refine_context_size"));

        // Load new options
        this.setRealtimeAnalysisEnabled(burpPreferences.getBoolean("config.scanner.realtime_analysis_enabled"));
        this.setHistoryScanLimit(burpPreferences.getInteger("config.scanner.history_scan_limit"));
        this.setToolSources(burpPreferences.getString("config.scanner.tool_sources"));
        this.setOriginFilter(burpPreferences.getString("config.scanner.origin_filter"));

        // Load selected importance levels from comma-separated string
        String savedLevels = burpPreferences.getString("config.scanner.selected_importance_levels");
        if (savedLevels != null && !savedLevels.isEmpty()) {
            this.selectedImportanceLevels = new java.util.HashSet<>();
            for (String level : savedLevels.split(",")) {
                try {
                    this.selectedImportanceLevels.add(Integer.parseInt(level.trim()));
                } catch (NumberFormatException e) {
                    // Ignore invalid values
                }
            }
        }

        this.setYamlAutoWatchPath(burpPreferences.getString("config.scanner.yaml_autowatch_path"));
        this.setYamlAutoWatchEnabled(burpPreferences.getBoolean("config.scanner.yaml_autowatch_enabled"));
    }

    private void loadRegexes(boolean useOnlyDefaults) {
        String json;

        this.generalRegexList.clear();
        json = burpPreferences.getString("config.scanner.general_regexes");
        if (useOnlyDefaults || Objects.isNull(json) || json.isBlank())
            this.generalRegexList.addAll(RegexSeeder.getGeneralRegexes());
        else FileUtils.importRegexListFromJSON(json, this.generalRegexList, true);

        this.extensionsRegexList.clear();
        json = burpPreferences.getString("config.scanner.extensions_regexes");
        if (useOnlyDefaults || Objects.isNull(json) || json.isBlank())
            this.extensionsRegexList.addAll(RegexSeeder.getExtensionRegexes());
        else FileUtils.importRegexListFromJSON(json, this.extensionsRegexList, true);
    }

    /**
     * Save the scanner options in Burp's persistent preference store.
     */
    public void saveToPersistentStorage() {
        burpPreferences.setInteger("config.scanner.max_response_size", this.getConfigMaxResponseSize());
        burpPreferences.setInteger("config.scanner.number_of_threads", this.getConfigNumberOfThreads());
        burpPreferences.setInteger("config.scanner.refine_context_size", this.getConfigRefineContextSize());
        burpPreferences.setBoolean("config.scanner.filter.in_scope", this.isFilterInScopeCheckbox());
        burpPreferences.setBoolean("config.scanner.filter.skip_max_size", this.isFilterSkipMaxSizeCheckbox());
        burpPreferences.setBoolean("config.scanner.filter.skip_media_type", this.isFilterSkipMediaTypeCheckbox());

        // Save new options
        burpPreferences.setBoolean("config.scanner.realtime_analysis_enabled", this.isRealtimeAnalysisEnabled());
        burpPreferences.setInteger("config.scanner.history_scan_limit", this.getHistoryScanLimit());
        burpPreferences.setString("config.scanner.tool_sources", this.getToolSources());
        burpPreferences.setString("config.scanner.origin_filter", this.getOriginFilter());

        // Save selected importance levels as comma-separated string
        String levelsStr = this.selectedImportanceLevels.stream()
            .sorted()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));
        burpPreferences.setString("config.scanner.selected_importance_levels", levelsStr);

        burpPreferences.setString("config.scanner.yaml_autowatch_path", this.getYamlAutoWatchPath());
        burpPreferences.setBoolean("config.scanner.yaml_autowatch_enabled", this.isYamlAutoWatchEnabled());

        burpPreferences.setString("config.scanner.general_regexes", FileUtils.exportRegexListToJson(this.generalRegexList, true));
        burpPreferences.setString("config.scanner.extensions_regexes", FileUtils.exportRegexListToJson(this.extensionsRegexList, true));
    }

    public void resetToDefaults(boolean resetOptions, boolean resetRegexes) {
        if (resetOptions) loadOptionsDefaults();
        if (resetRegexes) loadRegexes(true);
    }

    public boolean isFilterInScopeCheckbox() {
        return filterInScopeCheckbox;
    }

    public void setFilterInScopeCheckbox(Boolean filterInScopeCheckbox) {
        if (Objects.isNull(filterInScopeCheckbox)) return;
        this.filterInScopeCheckbox = filterInScopeCheckbox;
    }

    public boolean isFilterSkipMaxSizeCheckbox() {
        return filterSkipMaxSizeCheckbox;
    }

    public void setFilterSkipMaxSizeCheckbox(Boolean filterSkipMaxSizeCheckbox) {
        if (Objects.isNull(filterSkipMaxSizeCheckbox)) return;
        this.filterSkipMaxSizeCheckbox = filterSkipMaxSizeCheckbox;
    }

    public boolean isFilterSkipMediaTypeCheckbox() {
        return filterSkipMediaTypeCheckbox;
    }

    public void setFilterSkipMediaTypeCheckbox(Boolean filterSkipMediaTypeCheckbox) {
        if (Objects.isNull(filterSkipMediaTypeCheckbox)) return;
        this.filterSkipMediaTypeCheckbox = filterSkipMediaTypeCheckbox;
    }

    public int getConfigMaxResponseSize() {
        return configMaxResponseSize;
    }

    public void setConfigMaxResponseSize(Integer configMaxResponseSize) {
        if (Objects.isNull(configMaxResponseSize)) return;
        this.configMaxResponseSize = configMaxResponseSize;
    }

    public int getConfigNumberOfThreads() {
        return configNumberOfThreads;
    }

    public void setConfigNumberOfThreads(Integer configNumberOfThreads) {
        if (Objects.isNull(configNumberOfThreads)) return;
        this.configNumberOfThreads = configNumberOfThreads;
    }

    public int getConfigRefineContextSize() {
        return configRefineContextSize;
    }

    public void setConfigRefineContextSize(Integer configRefineContextSize) {
        if (Objects.isNull(configRefineContextSize)) return;
        this.configRefineContextSize = configRefineContextSize;
    }

    public List<RegexEntity> getGeneralRegexList() {
        return generalRegexList;
    }

    public List<RegexEntity> getExtensionsRegexList() {
        return extensionsRegexList;
    }

    // New option getters and setters
    public boolean isRealtimeAnalysisEnabled() {
        return realtimeAnalysisEnabled;
    }

    public void setRealtimeAnalysisEnabled(Boolean realtimeAnalysisEnabled) {
        if (Objects.isNull(realtimeAnalysisEnabled)) return;
        this.realtimeAnalysisEnabled = realtimeAnalysisEnabled;
    }

    public int getHistoryScanLimit() {
        return historyScanLimit;
    }

    public void setHistoryScanLimit(Integer historyScanLimit) {
        if (Objects.isNull(historyScanLimit)) return;
        this.historyScanLimit = historyScanLimit;
    }

    public String getToolSources() {
        return toolSources;
    }

    public void setToolSources(String toolSources) {
        if (Objects.isNull(toolSources)) return;
        this.toolSources = toolSources;
    }

    public String getOriginFilter() {
        return originFilter;
    }

    public void setOriginFilter(String originFilter) {
        if (Objects.isNull(originFilter)) return;
        this.originFilter = originFilter;
    }

    public java.util.Set<Integer> getSelectedImportanceLevels() {
        return selectedImportanceLevels;
    }

    public void setSelectedImportanceLevels(java.util.Set<Integer> selectedImportanceLevels) {
        if (Objects.isNull(selectedImportanceLevels)) return;
        this.selectedImportanceLevels = selectedImportanceLevels;
    }

    public boolean isImportanceLevelSelected(int level) {
        return selectedImportanceLevels.contains(level);
    }

    public void toggleImportanceLevel(int level) {
        if (selectedImportanceLevels.contains(level)) {
            selectedImportanceLevels.remove(level);
        } else {
            selectedImportanceLevels.add(level);
        }
        notifyImportanceChange();
    }

    /**
     * Add a listener to be notified when importance levels change
     */
    public void addImportanceChangeListener(Runnable listener) {
        importanceChangeListeners.add(listener);
    }

    /**
     * Notify all listeners that importance levels have changed
     */
    private void notifyImportanceChange() {
        for (Runnable listener : importanceChangeListeners) {
            listener.run();
        }
    }

    public String getYamlAutoWatchPath() {
        return yamlAutoWatchPath;
    }

    public void setYamlAutoWatchPath(String yamlAutoWatchPath) {
        if (Objects.isNull(yamlAutoWatchPath)) return;
        this.yamlAutoWatchPath = yamlAutoWatchPath;
    }

    public boolean isYamlAutoWatchEnabled() {
        return yamlAutoWatchEnabled;
    }

    public void setYamlAutoWatchEnabled(Boolean yamlAutoWatchEnabled) {
        if (Objects.isNull(yamlAutoWatchEnabled)) return;
        this.yamlAutoWatchEnabled = yamlAutoWatchEnabled;
    }
}
