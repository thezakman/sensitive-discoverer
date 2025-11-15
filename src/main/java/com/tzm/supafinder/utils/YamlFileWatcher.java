package com.tzm.supafinder.utils;

import com.tzm.supafinder.model.RegexEntity;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * File watcher for automatic YAML file monitoring and reloading
 */
public class YamlFileWatcher {
    private final Path watchPath;
    private WatchService watchService;
    private Thread watchThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Consumer<List<RegexEntity>> onReloadCallback;

    /**
     * Create a new YAML file watcher
     * @param watchDirectory Directory to watch for YAML file changes
     * @param onReloadCallback Callback to invoke when YAML files are reloaded
     */
    public YamlFileWatcher(File watchDirectory, Consumer<List<RegexEntity>> onReloadCallback) throws IOException {
        if (!watchDirectory.exists() || !watchDirectory.isDirectory()) {
            throw new IllegalArgumentException("Watch path must be an existing directory: " + watchDirectory.getAbsolutePath());
        }

        this.watchPath = watchDirectory.toPath();
        this.onReloadCallback = onReloadCallback;
    }

    /**
     * Start watching for file changes
     */
    public void start() throws IOException {
        if (running.get()) {
            return; // Already running
        }

        watchService = FileSystems.getDefault().newWatchService();
        registerDirectory(watchPath);

        running.set(true);

        watchThread = new Thread(() -> {
            try {
                while (running.get()) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    boolean shouldReload = false;

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == OVERFLOW) {
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path filename = ev.context();

                        // Check if it's a YAML file
                        if (isYamlFile(filename)) {
                            shouldReload = true;

                            Path fullPath = watchPath.resolve(filename);
                            if (kind == ENTRY_CREATE) {
                                System.out.println("YAML file created: " + fullPath);
                            } else if (kind == ENTRY_MODIFY) {
                                System.out.println("YAML file modified: " + fullPath);
                            } else if (kind == ENTRY_DELETE) {
                                System.out.println("YAML file deleted: " + fullPath);
                            }
                        }
                    }

                    // Reload all YAML files if any changes detected
                    if (shouldReload) {
                        reloadAllYamlFiles();
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in YAML file watcher: " + e.getMessage());
                e.printStackTrace();
            }
        }, "YAML-FileWatcher");

        watchThread.setDaemon(true);
        watchThread.start();

        System.out.println("YAML file watcher started for: " + watchPath);
    }

    /**
     * Stop watching for file changes
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        if (watchThread != null) {
            watchThread.interrupt();
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                System.err.println("Error closing watch service: " + e.getMessage());
            }
        }

        System.out.println("YAML file watcher stopped");
    }

    /**
     * Check if the watcher is currently running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Register a directory (and optionally subdirectories) with the watch service
     */
    private void registerDirectory(Path dir) throws IOException {
        dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

        // Register subdirectories as well for recursive watching
        Files.walk(dir)
            .filter(Files::isDirectory)
            .forEach(subDir -> {
                if (!subDir.equals(dir)) {
                    try {
                        subDir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    } catch (IOException e) {
                        System.err.println("Failed to register subdirectory: " + subDir + " - " + e.getMessage());
                    }
                }
            });
    }

    /**
     * Check if a file is a YAML file
     */
    private boolean isYamlFile(Path path) {
        String filename = path.toString().toLowerCase();
        return filename.endsWith(".yaml") || filename.endsWith(".yml");
    }

    /**
     * Reload all YAML files from the watch directory
     */
    private void reloadAllYamlFiles() {
        try {
            List<RegexEntity> entities = YamlParser.parseYamlDirectory(watchPath.toFile());
            System.out.println("Reloaded " + entities.size() + " YAML patterns");

            if (onReloadCallback != null) {
                onReloadCallback.accept(entities);
            }
        } catch (IOException e) {
            System.err.println("Error reloading YAML files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Manually trigger a reload of all YAML files
     */
    public void triggerReload() {
        reloadAllYamlFiles();
    }
}
