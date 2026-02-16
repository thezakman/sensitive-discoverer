package com.tzm.supafinder;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import com.tzm.supafinder.model.RegexEntity;
import com.tzm.supafinder.model.RegexScannerOptions;
import com.tzm.supafinder.ui.tab.AboutTab;
import com.tzm.supafinder.ui.tab.ApplicationTab;
import com.tzm.supafinder.ui.tab.LoggerTab;
import com.tzm.supafinder.ui.tab.OptionsTab;
import com.tzm.supafinder.utils.SwingUtils;
import com.tzm.supafinder.utils.YamlFileWatcher;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static com.tzm.supafinder.utils.Utils.loadConfigFile;

public class MainUI {
    private final MontoyaApi burpApi;
    private final Properties configProperties;
    private final RegexScannerOptions scannerOptions;
    private JTabbedPane mainPanel;
    private boolean interfaceInitialized;
    private LoggerTab loggerTab;
    private burp.api.montoya.core.Registration httpHandlerRegistration;
    private YamlFileWatcher yamlFileWatcher;

    public MainUI(MontoyaApi burpApi) throws Exception {
        this.interfaceInitialized = false;
        this.burpApi = burpApi;
        this.configProperties = loadConfigFile();
        this.scannerOptions = new RegexScannerOptions(this.configProperties, this.burpApi.persistence().preferences());
    }

    public boolean isInterfaceInitialized() {
        return interfaceInitialized;
    }

    public RegexScannerOptions getScannerOptions() {
        return scannerOptions;
    }

    /**
     * Main function that initializes the extension and creates the UI, asynchronously
     */
    public void initializeUI() {
        SwingUtilities.invokeLater(this::_initializeUI);
    }

    /**
     * UI initialization logic that must run in the EDT
     */
    private void _initializeUI() {
        SwingUtils.assertIsEDT();

        mainPanel = new JTabbedPane();
        loggerTab = new LoggerTab(this);
        mainPanel.addTab(loggerTab.getTabName(), loggerTab.getPanel());
        ApplicationTab optionsTab = new OptionsTab(this.getScannerOptions(), this.burpApi);
        mainPanel.addTab(optionsTab.getTabName(), optionsTab.getPanel());
        ApplicationTab aboutTab = new AboutTab();
        mainPanel.addTab(aboutTab.getTabName(), aboutTab.getPanel());

        burpApi.userInterface().applyThemeToComponent(mainPanel);
        burpApi.userInterface().registerSuiteTab(this.getExtensionName(), this.getMainPanel());

        // Initialize YAML file watcher if auto-watch is enabled
        initializeYamlWatcher();

        this.interfaceInitialized = true;
    }

    /**
     * Toggle real-time analysis on/off
     */
    public void toggleRealtimeAnalysis(boolean enabled) {
        if (enabled) {
            // Register HTTP handler for real-time analysis
            if (httpHandlerRegistration == null && loggerTab != null) {
                // Create a simple HTTP handler that uses the scanner
                HttpHandler httpHandler = new HttpHandler() {
                    @Override
                    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
                        if (shouldAnalyzeToolSource(requestToBeSent.toolSource().toString())) {
                            analyzeRequestAsync(requestToBeSent);
                        }
                        return RequestToBeSentAction.continueWith(requestToBeSent);
                    }

                    @Override
                    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
                        if (shouldAnalyzeToolSource(responseReceived.toolSource().toString())) {
                            analyzeResponseAsync(responseReceived);
                        }
                        return ResponseReceivedAction.continueWith(responseReceived);
                    }

                    private boolean shouldAnalyzeToolSource(String toolSource) {
                        String configuredSources = scannerOptions.getToolSources();
                        System.out.println("[DEBUG] Tool source check - Received: '" + toolSource + "', Configured: '" + configuredSources + "'");

                        if (configuredSources == null || configuredSources.isEmpty()) {
                            System.out.println("[DEBUG] No sources configured, blocking all");
                            return false; // Block everything if nothing is selected
                        }

                        java.util.Set<String> sources = java.util.Arrays.stream(configuredSources.split(","))
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .collect(java.util.stream.Collectors.toSet());
                        boolean shouldAnalyze = sources.contains("ALL") || sources.contains(toolSource.trim().toUpperCase());
                        System.out.println("[DEBUG] Sources set: " + sources + ", Should analyze: " + shouldAnalyze);
                        return shouldAnalyze;
                    }

                    private void analyzeRequestAsync(HttpRequestToBeSent request) {
                        new Thread(() -> {
                            try {
                                burp.api.montoya.http.message.HttpRequestResponse requestResponse =
                                    burp.api.montoya.http.message.HttpRequestResponse.httpRequestResponse(request, null);
                                loggerTab.getRegexScanner().analyzeSingleMessageRealtime(requestResponse);
                            } catch (Exception e) {
                                System.err.println("Error analyzing request in real-time: " + e.getMessage());
                            }
                        }, "RealTime-Request-Analyzer").start();
                    }

                    private void analyzeResponseAsync(HttpResponseReceived response) {
                        new Thread(() -> {
                            try {
                                burp.api.montoya.http.message.HttpRequestResponse requestResponse =
                                    burp.api.montoya.http.message.HttpRequestResponse.httpRequestResponse(
                                        response.initiatingRequest(), response);
                                loggerTab.getRegexScanner().analyzeSingleMessageRealtime(requestResponse);
                            } catch (Exception e) {
                                System.err.println("Error analyzing response in real-time: " + e.getMessage());
                            }
                        }, "RealTime-Response-Analyzer").start();
                    }
                };

                httpHandlerRegistration = burpApi.http().registerHttpHandler(httpHandler);
                System.out.println("Real-time analysis enabled");
            }
        } else {
            // Unregister HTTP handler
            if (httpHandlerRegistration != null) {
                httpHandlerRegistration.deregister();
                httpHandlerRegistration = null;
                System.out.println("Real-time analysis disabled");
            }
        }
    }

    /**
     * Initialize YAML file watcher
     */
    private void initializeYamlWatcher() {
        String watchPath = scannerOptions.getYamlAutoWatchPath();
        if (scannerOptions.isYamlAutoWatchEnabled() && watchPath != null && !watchPath.isEmpty()) {
            try {
                File watchDir = new File(watchPath);
                if (watchDir.exists() && watchDir.isDirectory()) {
                    yamlFileWatcher = new YamlFileWatcher(watchDir, this::onYamlFilesReloaded);
                    yamlFileWatcher.start();
                    System.out.println("YAML file watcher started for: " + watchPath);
                }
            } catch (IOException e) {
                System.err.println("Failed to start YAML file watcher: " + e.getMessage());
            }
        }
    }

    /**
     * Callback when YAML files are reloaded
     */
    private void onYamlFilesReloaded(List<RegexEntity> entities) {
        System.out.println("Reloaded " + entities.size() + " YAML patterns");
        // Add to general regex list
        scannerOptions.getGeneralRegexList().addAll(entities);
        SwingUtilities.invokeLater(() -> {
            // Update UI if needed
            System.out.println("YAML patterns added to general list");
        });
    }

    /**
     * Returns the extension's main panel
     */
    public JTabbedPane getMainPanel() {
        return mainPanel;
    }

    public MontoyaApi getBurpApi() {
        return burpApi;
    }

    /**
     * getExtensionName return the name of the extension from the configuration file
     */
    public String getExtensionName() {
        return configProperties.getProperty("ui.extension_name");
    }

    public static final class UIOptions {
        public static final Font H1_FONT = new Font("SansSerif", Font.BOLD, 16);
        public static final Font H2_FONT = new Font("SansSerif", Font.BOLD, 14);
        public static final Color ACCENT_COLOR = new Color(255, 102, 51);
    }
}
