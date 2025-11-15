package com.tzm.supafinder;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.MimeType;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import com.tzm.supafinder.model.HttpRecord;
import com.tzm.supafinder.model.HttpSection;
import com.tzm.supafinder.model.LogEntriesManager;
import com.tzm.supafinder.model.LogEntity;
import com.tzm.supafinder.model.RegexEntity;
import com.tzm.supafinder.model.RegexScannerOptions;
import com.tzm.supafinder.utils.BurpUtils;
import com.tzm.supafinder.utils.ScannerUtils;

import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Class to perform scans of HTTP items using regexes.
 * <br><br>
 * <b>Warning</b>: this class doesn't support concurrent scans within a single instance.
 */
public class RegexScanner {
    /**
     * List of MIME types to ignore while scanning when the relevant option is enabled
     */
    public static final EnumSet<MimeType> blacklistedMimeTypes = EnumSet.of(
            MimeType.APPLICATION_FLASH,
            MimeType.FONT_WOFF,
            MimeType.FONT_WOFF2,
            MimeType.IMAGE_BMP,
            MimeType.IMAGE_GIF,
            MimeType.IMAGE_JPEG,
            MimeType.IMAGE_PNG,
            MimeType.IMAGE_SVG_XML,
            MimeType.IMAGE_TIFF,
            MimeType.IMAGE_UNKNOWN,
            MimeType.LEGACY_SER_AMF,
            MimeType.RTF,
            MimeType.SOUND,
            MimeType.VIDEO
    );
    private final MontoyaApi burpApi;
    private final RegexScannerOptions scannerOptions;
    private final List<RegexEntity> generalRegexList;
    private final List<RegexEntity> extensionsRegexList;
    private final Object analyzeLock = new Object();
    /**
     * Flag that indicates if the scan must be interrupted.
     * Used to interrupt scan before completion.
     */
    private volatile boolean interruptScan;
    /**
     * Counter of analyzed items. Used mainly for the progress bar
     */
    private int analyzedItems;
    /**
     * Reference to a progress bar to update during the scan
     */
    private JProgressBar progressBar;
    /**
     * Reference to log entries manager for real-time analysis
     */
    private LogEntriesManager logEntriesManager;

    public RegexScanner(MontoyaApi burpApi, RegexScannerOptions scannerOptions) {
        this.burpApi = burpApi;
        this.scannerOptions = scannerOptions;
        this.generalRegexList = scannerOptions.getGeneralRegexList();
        this.extensionsRegexList = scannerOptions.getExtensionsRegexList();
        this.interruptScan = false;
        this.progressBar = null;
    }

    private void setupAnalysis(int maxItems) {
        this.analyzedItems = 0;
        if (Objects.nonNull(progressBar)) SwingUtilities.invokeLater(() -> {
            progressBar.setMaximum(maxItems);
            progressBar.setValue(0);
        });
    }

    /**
     * Method for analyzing the elements in Burp > Proxy > HTTP history
     *
     * @param logEntriesCallback A callback that's called for every new finding, with a LogEntity as the only argument
     */
    public void analyzeProxyHistory(Consumer<LogEntity> logEntriesCallback) {
        // create a copy of the regex list to protect from changes while scanning
        // Filter by importance level
        List<RegexEntity> allRegexListCopy = Stream
                .concat(generalRegexList.stream(), extensionsRegexList.stream())
                .map(RegexEntity::new)
                .filter(regex -> scannerOptions.getSelectedImportanceLevels().contains(regex.getImportance()))
                .toList();

        System.out.println("[DEBUG] Starting proxy history analysis");
        System.out.println("[DEBUG] General regexes: " + generalRegexList.size());
        System.out.println("[DEBUG] Extension regexes: " + extensionsRegexList.size());
        System.out.println("[DEBUG] Selected importance levels: " + scannerOptions.getSelectedImportanceLevels());
        System.out.println("[DEBUG] Filtered regexes to scan: " + allRegexListCopy.size());

        ExecutorService executor = Executors.newFixedThreadPool(scannerOptions.getConfigNumberOfThreads());

        // removing items from the list allows the GC to clean up just after the task is executed
        // instead of waiting until the whole analysis finishes.
        List<ProxyHttpRequestResponse> proxyEntries = this.burpApi.proxy().history();
        System.out.println("[DEBUG] Proxy history entries: " + proxyEntries.size());
        if (proxyEntries.isEmpty()) return;

        // Apply history scan limit
        int limit = scannerOptions.getHistoryScanLimit();
        int actualSize = proxyEntries.size();
        if (limit > 0 && limit < actualSize) {
            // Keep only the last 'limit' entries (most recent)
            proxyEntries = proxyEntries.subList(actualSize - limit, actualSize);
        }

        this.setupAnalysis(proxyEntries.size());

        for (int entryIndex = proxyEntries.size() - 1; entryIndex >= 0; entryIndex--) {
            ProxyHttpRequestResponse proxyEntry = proxyEntries.remove(entryIndex);
            executor.execute(() -> {
                if (interruptScan) return;

                analyzeSingleMessage(allRegexListCopy, scannerOptions, proxyEntry, logEntriesCallback);

                synchronized (analyzeLock) {
                    this.analyzedItems++;
                }
                if (Objects.nonNull(progressBar))
                    SwingUtilities.invokeLater(() -> progressBar.setValue(this.analyzedItems));
            });
        }

        try {
            executor.shutdown();
            while (!executor.isTerminated()) {
                if (this.interruptScan)
                    executor.shutdownNow();

                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    /**
     * The main method that scan for regex in the single request body
     *
     * @param regexList          list of regexes to try and match
     * @param scannerOptions     options for the scanner
     * @param proxyEntry         the item (request/response) from burp's http proxy
     * @param logEntriesCallback A callback that's called for every new finding, with a LogEntity as the only argument.
     */
    private void analyzeSingleMessage(List<RegexEntity> regexList,
                                      RegexScannerOptions scannerOptions,
                                      ProxyHttpRequestResponse proxyEntry,
                                      Consumer<LogEntity> logEntriesCallback) {
        // The initial checks must be kept ordered based on the amount of information required from Burp APIs.
        // API calls (to MontoyaAPI) for specific parts of the request/response are quite slow.
        HttpRequest request = proxyEntry.finalRequest();
        if (ScannerUtils.isUrlOutOfScope(scannerOptions, request)) return;
        HttpResponse response = proxyEntry.response();
        if (ScannerUtils.isResponseEmpty(response)) return;
        if (ScannerUtils.isMimeTypeBlacklisted(scannerOptions, response)) return;
        ByteArray responseBody = response.body();
        if (ScannerUtils.isResponseSizeOverMaxSize(scannerOptions, responseBody)) return;

        // Not using bodyToString() as it's extremely slow
        String requestUrl = request.url();
        String requestBodyDecoded = BurpUtils.convertByteArrayToString(request.body());
        String requestHeaders = BurpUtils.convertHttpHeaderListToString(request.headers());
        String responseBodyDecoded = BurpUtils.convertByteArrayToString(responseBody);
        String responseHeaders = BurpUtils.convertHttpHeaderListToString(response.headers());

        for (RegexEntity regex : regexList) {
            if (this.interruptScan) return;
            if (!regex.isActive()) continue;

            Consumer<HttpMatchResult> logMatchCallback = match -> {
                System.out.println("[DEBUG] Match found! Regex: " + regex.getDescription() + ", Section: " + match.section + ", Match: " + match.match.substring(0, Math.min(50, match.match.length())));
                logEntriesCallback.accept(new LogEntity(request, response, regex, match.section, match.match));
            };
            HttpRecord requestResponse = new HttpRecord(requestUrl, requestHeaders, requestBodyDecoded, responseHeaders, responseBodyDecoded);
            performMatchingOnMessage(regex, scannerOptions, requestResponse, logMatchCallback);
        }
    }

    private void performMatchingOnMessage(RegexEntity regex,
                                          RegexScannerOptions scannerOptions,
                                          HttpRecord requestResponse,
                                          Consumer<HttpMatchResult> logMatchCallback) {
        Pattern regexCompiled = regex.getRegexCompiled();
        Optional<Pattern> refinerRegexCompiled = regex.getRefinerRegexCompiled();

        regex.getSections()
                .stream()
                .map(httpSection -> ScannerUtils.getHttpRecordSection(requestResponse, httpSection))
                .flatMap(sectionRecord -> {
                    String content = sectionRecord.content();

                    // Precheck optimization: if precheck is needed and fails, skip regex matching
                    if (regex.isPrecheckNeeded() && regex.getPrechecks() != null && !regex.getPrechecks().isEmpty()) {
                        boolean precheckPassed = false;
                        for (String precheck : regex.getPrechecks()) {
                            if (content.contains(precheck)) {
                                precheckPassed = true;
                                break;
                            }
                        }
                        if (!precheckPassed) {
                            return Stream.empty(); // Skip this section
                        }
                    }

                    Matcher matcher = regexCompiled.matcher(content);
                    Stream<HttpMatchResult> results = matcher.results().map(result -> {
                        String match = result.group();

                        // Apply refiner regex if present
                        if (refinerRegexCompiled.isPresent()) {
                            int startIndex = result.start();
                            Matcher preMatch = refinerRegexCompiled.get().matcher(content);
                            preMatch.region(Math.max(startIndex - scannerOptions.getConfigRefineContextSize(), 0), startIndex);
                            if (preMatch.find())
                                match = preMatch.group() + match;
                        }

                        return new HttpMatchResult(sectionRecord.section(), match);
                    });

                    // Apply ignore patterns filter
                    if (regex.getIgnoreCompiledPatterns() != null && !regex.getIgnoreCompiledPatterns().isEmpty()) {
                        results = results.filter(matchResult -> {
                            // Check if match should be ignored
                            for (Pattern ignorePattern : regex.getIgnoreCompiledPatterns()) {
                                if (ignorePattern.matcher(matchResult.match()).find()) {
                                    return false; // Ignore this match
                                }
                            }
                            return true; // Keep this match
                        });
                    }

                    // Stop at first occurrence if configured
                    if (regex.isStopFirstOccurrence()) {
                        return results.limit(1);
                    }

                    return results;
                })
                .forEach(logMatchCallback);
    }

    /**
     * Analyze a single HTTP message in real-time (called by HttpHandler)
     * This method filters based on importance level and adds to log manager
     *
     * @param requestResponse The HTTP request/response to analyze
     */
    public void analyzeSingleMessageRealtime(burp.api.montoya.http.message.HttpRequestResponse requestResponse) {
        // Create a copy of the regex list
        List<RegexEntity> allRegexListCopy = Stream
                .concat(generalRegexList.stream(), extensionsRegexList.stream())
                .map(RegexEntity::new)
                .filter(regex -> regex.isActive())
                .filter(regex -> scannerOptions.getSelectedImportanceLevels().contains(regex.getImportance()))
                .toList();

        HttpRequest request = requestResponse.request();
        if (ScannerUtils.isUrlOutOfScope(scannerOptions, request)) return;

        HttpResponse response = requestResponse.response();
        // Response might be null for request-only analysis
        if (response == null) {
            // Analyze request only
            analyzeRequestOnly(allRegexListCopy, request);
            return;
        }

        if (ScannerUtils.isResponseEmpty(response)) return;
        if (ScannerUtils.isMimeTypeBlacklisted(scannerOptions, response)) return;
        ByteArray responseBody = response.body();
        if (ScannerUtils.isResponseSizeOverMaxSize(scannerOptions, responseBody)) return;

        // Analyze full request/response
        String requestUrl = request.url();
        String requestBodyDecoded = BurpUtils.convertByteArrayToString(request.body());
        String requestHeaders = BurpUtils.convertHttpHeaderListToString(request.headers());
        String responseBodyDecoded = BurpUtils.convertByteArrayToString(responseBody);
        String responseHeaders = BurpUtils.convertHttpHeaderListToString(response.headers());

        for (RegexEntity regex : allRegexListCopy) {
            if (!regex.isActive()) continue;

            Consumer<HttpMatchResult> logMatchCallback = match -> {
                LogEntity logEntity = new LogEntity(request, response, regex, match.section, match.match);
                if (logEntriesManager != null) {
                    logEntriesManager.add(logEntity);
                }
            };

            HttpRecord requestResponse2 = new HttpRecord(requestUrl, requestHeaders, requestBodyDecoded, responseHeaders, responseBodyDecoded);
            performMatchingOnMessage(regex, scannerOptions, requestResponse2, logMatchCallback);
        }
    }

    /**
     * Analyze only the request (when response is not available)
     */
    private void analyzeRequestOnly(List<RegexEntity> regexList, HttpRequest request) {
        String requestUrl = request.url();
        String requestBodyDecoded = BurpUtils.convertByteArrayToString(request.body());
        String requestHeaders = BurpUtils.convertHttpHeaderListToString(request.headers());

        for (RegexEntity regex : regexList) {
            if (!regex.isActive()) continue;

            // Filter to only request sections
            EnumSet<HttpSection> requestSections = EnumSet.noneOf(HttpSection.class);
            for (HttpSection section : regex.getSections()) {
                if (section == HttpSection.REQ_URL ||
                    section == HttpSection.REQ_HEADERS ||
                    section == HttpSection.REQ_BODY) {
                    requestSections.add(section);
                }
            }

            if (requestSections.isEmpty()) continue;

            HttpRecord requestOnly = new HttpRecord(requestUrl, requestHeaders, requestBodyDecoded, "", "");
            Consumer<HttpMatchResult> logMatchCallback = match -> {
                LogEntity logEntity = new LogEntity(request, null, regex, match.section, match.match);
                if (logEntriesManager != null) {
                    logEntriesManager.add(logEntity);
                }
            };

            performMatchingOnMessage(regex, scannerOptions, requestOnly, logMatchCallback);
        }
    }

    /**
     * Change the interrupt flag that dictates whether to stop the current scan
     *
     * @param interruptScan flag value. If true, scan gets interrupted as soon as possible.
     */
    public void setInterruptScan(boolean interruptScan) {
        this.interruptScan = interruptScan;
    }

    public void setProgressBar(JProgressBar progressBar) {
        this.progressBar = progressBar;
    }

    /**
     * Set the log entries manager for real-time analysis
     */
    public void setLogEntriesManager(LogEntriesManager logEntriesManager) {
        this.logEntriesManager = logEntriesManager;
    }

    private record HttpMatchResult(HttpSection section, String match) {
    }
}
