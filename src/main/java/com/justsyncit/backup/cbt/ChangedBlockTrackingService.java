package com.justsyncit.backup.cbt;

import com.justsyncit.scanner.AsyncScanOptions;
import com.justsyncit.scanner.AsyncWatchServiceManager;
import com.justsyncit.scanner.FileChangeEvent;
import com.justsyncit.scanner.ThreadPoolManager;
import com.justsyncit.scanner.AsyncByteBufferPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service that orchestrates Changed Block Tracking (CBT).
 * It manages the WatchService, persists events to a journal, and provides
 * queries for changed files to support incremental backups.
 */
public class ChangedBlockTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(ChangedBlockTrackingService.class);

    private final AsyncWatchServiceManager watchServiceManager;
    private final ModificationJournal journal;
    private final Map<Path, Instant> dirtyFiles = new ConcurrentHashMap<>();
    private final Set<Path> monitoredRoots = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new ChangedBlockTrackingService.
     *
     * @param threadPoolManager Thread pool manager for async operations
     * @param bufferPool        Buffer pool
     * @param journalDir        Directory to store the modification journal
     */
    public ChangedBlockTrackingService(ThreadPoolManager threadPoolManager,
            AsyncByteBufferPool bufferPool,
            Path journalDir) {
        // Create a default configuration for the watch service
        AsyncScanOptions options = new AsyncScanOptions()
                .withEventDebouncingEnabled(true)
                .withDebounceTimeoutMs(500); // 500ms debounce

        this.watchServiceManager = new AsyncWatchServiceManager(threadPoolManager, bufferPool, options);
        this.journal = new ModificationJournal(journalDir);
    }

    /**
     * Starts the tracking service.
     * Initializes the journal and the watch service manager.
     */
    public void start() {
        try {
            logger.info("Starting ChangedBlockTrackingService...");

            // 1. Initialize Journal
            journal.init();

            // 2. Replay history to build in-memory state
            replayJournal();

            // 3. Start WatchServiceManager
            watchServiceManager.start();

            logger.info("ChangedBlockTrackingService started successfully.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to start ChangedBlockTrackingService", e);
        }
    }

    /**
     * Stops the tracking service.
     */
    public void stop() {
        logger.info("Stopping ChangedBlockTrackingService...");
        watchServiceManager.stopAsync().join();
        try {
            journal.close();
        } catch (IOException e) {
            logger.error("Error closing modification journal", e);
        }
        logger.info("ChangedBlockTrackingService stopped.");
    }

    /**
     * Enables tracking for a specific directory.
     *
     * @param rootDir the directory to monitor
     */
    public void enableTracking(Path rootDir) {
        Path absolutePath = rootDir.toAbsolutePath().normalize();

        if (monitoredRoots.contains(absolutePath)) {
            logger.debug("Tracking already enabled for: {}", absolutePath);
            return;
        }

        logger.info("Enabling CBT for: {}", absolutePath);
        monitoredRoots.add(absolutePath);

        // Register with WatchService
        // We use recursive watching
        AsyncScanOptions options = new AsyncScanOptions()
                .withRecursiveWatching(true)
                .withWatchEventKinds(Set.of("ENTRY_MODIFY", "ENTRY_CREATE", "ENTRY_DELETE"));

        watchServiceManager.startDirectoryMonitoring(absolutePath, options, this::handleFileChangeEvent);
    }

    /**
     * Disables tracking for a directory.
     *
     * @param rootDir the directory to stop monitoring
     */
    public void disableTracking(Path rootDir) {
        Path absolutePath = rootDir.toAbsolutePath().normalize();
        // Implementation note: We'd need to map rootDir to the specific Registration ID
        // to stop it cleanly.
        // For now, we mainly focus on starting.
        // TODO: Implement clean stop by tracking Registration IDs.
        monitoredRoots.remove(absolutePath);
    }

    /**
     * Returns a list of files that have changed since the given timestamp.
     *
     * @param rootDir root directory filter
     * @param since   timestamp to check from
     * @return list of changed file paths
     */
    public List<Path> getChangedFiles(Path rootDir, Instant since) {
        Path normalizedRoot = rootDir.toAbsolutePath().normalize();

        return dirtyFiles.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(normalizedRoot)) // Under this root
                .filter(entry -> entry.getValue().isAfter(since)) // Changed after 'since'
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Cleans up events older than the specific timestamp.
     * Compacts the journal and removes old entries from in-memory state.
     *
     * @param beforeTimestamp cutoff timestamp
     */
    public void cleanupEventsBefore(Instant beforeTimestamp) {
        try {
            journal.compact(beforeTimestamp);
            // Cleanup in-memory map to free memory
            dirtyFiles.entrySet().removeIf(entry -> entry.getValue().isBefore(beforeTimestamp));
            logger.info("Cleaned up CBT events before {}", beforeTimestamp);
        } catch (IOException e) {
            logger.error("Failed to compact journal", e);
        }
    }

    private void handleFileChangeEvent(FileChangeEvent event) {
        // 1. Update in-memory state
        dirtyFiles.put(event.getFilePath(), event.getEventTime());

        // 2. Persist to journal
        journal.recordEvent(event);

        logger.debug("Recorded change: {} at {}", event.getFilePath(), event.getEventTime());
    }

    private void replayJournal() throws IOException {
        logger.info("Replaying modification journal...");
        List<FileChangeEvent> history = journal.replayEvents();
        int count = 0;
        for (FileChangeEvent event : history) {
            dirtyFiles.put(event.getFilePath(), event.getEventTime());
            count++;
        }
        logger.info("Replayed {} events. Current dirty file count: {}", count, dirtyFiles.size());
    }
}
