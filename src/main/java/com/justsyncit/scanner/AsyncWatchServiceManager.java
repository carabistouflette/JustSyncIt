/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages multiple WatchService instances for real-time file system monitoring.
 * Provides non-blocking event processing with async callbacks and resource
 * management.
 */

/**
 * Manages multiple WatchService instances for real-time file system monitoring.
 * Provides non-blocking event processing with async callbacks and resource
 * management.
 */

public class AsyncWatchServiceManager {

    private static final Logger logger = LoggerFactory.getLogger(AsyncWatchServiceManager.class);

    /** Thread pool manager for async operations. */
    private final ThreadPoolManager threadPoolManager;

    /** Async buffer pool for memory management. */
    private final AsyncByteBufferPool asyncBufferPool;

    /** Active watch service registrations. */
    private final Map<String, WatchServiceRegistration> activeRegistrations;

    /** Watch service instances by directory. */
    private final Map<Path, WatchService> watchServices;

    /** Event processing queue. */
    private final BlockingQueue<FileChangeEvent> eventQueue;

    /** Event processing executor. */
    private final ExecutorService eventProcessingExecutor;

    /** Debounce timers for event batching. */
    private final Map<Path, ScheduledFuture<?>> debounceTimers;

    /** Event batching configuration. */
    private final AsyncScanOptions configuration;

    /** Manager state. */
    private final AtomicBoolean running;

    /** Statistics tracking. */
    private final AsyncScannerStats stats;

    /** Event handlers by registration ID. */
    private final Map<String, Consumer<FileChangeEvent>> eventHandlers;

    /**
     * Creates a new AsyncWatchServiceManager.
     *
     * @param threadPoolManager thread pool manager for async operations
     * @param asyncBufferPool   async buffer pool for memory management
     * @param configuration     default configuration for watch services
     */
    public AsyncWatchServiceManager(ThreadPoolManager threadPoolManager,
            AsyncByteBufferPool asyncBufferPool,
            AsyncScanOptions configuration) {
        this.threadPoolManager = Objects.requireNonNull(threadPoolManager);
        this.asyncBufferPool = Objects.requireNonNull(asyncBufferPool);
        this.configuration = Objects.requireNonNull(configuration);
        this.activeRegistrations = new ConcurrentHashMap<>();
        this.watchServices = new ConcurrentHashMap<>();
        this.eventQueue = new LinkedBlockingQueue<>();
        this.eventProcessingExecutor = threadPoolManager.getWatchServiceThreadPool();
        this.debounceTimers = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.stats = new AsyncScannerStats();
        this.eventHandlers = new ConcurrentHashMap<>();
    }

    /**
     * Starts the watch service manager.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting AsyncWatchServiceManager");

            // Start event processing loop
            eventProcessingExecutor.submit(this::processEventsLoop);

            logger.info("AsyncWatchServiceManager started successfully");
        }
    }

    /**
     * Stops the watch service manager and cleans up resources.
     *
     * @return a CompletableFuture that completes when shutdown is done
     */
    public CompletableFuture<Void> stopAsync() {
        if (!running.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("Stopping AsyncWatchServiceManager");

        return CompletableFuture.runAsync(() -> {
            try {
                // Cancel all debounce timers
                debounceTimers.values().forEach(future -> future.cancel(false));
                debounceTimers.clear();

                // Stop all active registrations
                List<CompletableFuture<Void>> stopFutures = new ArrayList<>();
                activeRegistrations.values().forEach(registration -> {
                    stopFutures.add(stopDirectoryMonitoring(registration));
                });

                // Wait for all registrations to stop
                CompletableFuture.allOf(stopFutures.toArray(new CompletableFuture<?>[0]))
                        .get(30, TimeUnit.SECONDS);

                // Shutdown event processing executor
                eventProcessingExecutor.shutdown();
                eventProcessingExecutor.awaitTermination(10, TimeUnit.SECONDS);

                // Clear all collections
                activeRegistrations.clear();
                watchServices.clear();
                eventQueue.clear();
                eventHandlers.clear();

                logger.info("AsyncWatchServiceManager stopped successfully");

            } catch (Exception e) {
                logger.error("Error stopping AsyncWatchServiceManager", e);
                throw new RuntimeException("Failed to stop AsyncWatchServiceManager", e);
            }
        }, eventProcessingExecutor);
    }

    /**
     * Starts monitoring a directory for file changes.
     *
     * @param directory    directory to monitor
     * @param options      monitoring options
     * @param eventHandler handler for file change events
     * @return a CompletableFuture that completes with the registration
     */
    public CompletableFuture<WatchServiceRegistration> startDirectoryMonitoring(
            Path directory,
            AsyncScanOptions options,
            Consumer<FileChangeEvent> eventHandler) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!running.get()) {
                    throw new IllegalStateException("WatchServiceManager is not running");
                }

                Path absoluteDir = directory.toAbsolutePath().normalize();

                // Check if already registered
                Optional<WatchServiceRegistration> existing = activeRegistrations.values().stream()
                        .filter(reg -> reg.getMonitoredDirectory().equals(absoluteDir))
                        .findFirst();

                if (existing.isPresent()) {
                    logger.warn("Directory already being monitored: {}", absoluteDir);
                    return existing.get();
                }

                // Create or get existing watch service
                WatchService watchService = watchServices.computeIfAbsent(absoluteDir, this::createWatchService);

                // Register directory with watch service
                WatchKey watchKey = registerDirectory(watchService, absoluteDir, options);

                // Create registration
                WatchServiceRegistration registration = new WatchServiceRegistration(
                        absoluteDir, options.getWatchEventKinds(),
                        options.isRecursiveWatching(), options);

                // Store registration and handler
                activeRegistrations.put(registration.getRegistrationId(), registration);
                eventHandlers.put(registration.getRegistrationId(), eventHandler);

                // Update statistics
                stats.incrementWatchRegistrations();
                stats.incrementActiveWatchRegistrations();

                logger.info("Started monitoring directory: {} with registration: {}",
                        absoluteDir, registration.getRegistrationId());

                return registration;

            } catch (Exception e) {
                logger.error("Failed to start monitoring directory: {}", directory, e);
                stats.incrementWatchServiceErrors();
                throw new RuntimeException("Failed to start directory monitoring", e);
            }
        }, eventProcessingExecutor);
    }

    /**
     * Stops monitoring a directory.
     *
     * @param registration registration to stop
     * @return a CompletableFuture that completes when monitoring is stopped
     */
    public CompletableFuture<Void> stopDirectoryMonitoring(WatchServiceRegistration registration) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (registration == null || !registration.isActive()) {
                    return;
                }

                Path directory = registration.getMonitoredDirectory();
                String registrationId = registration.getRegistrationId();

                logger.info("Stopping monitoring for directory: {} with registration: {}",
                        directory, registrationId);

                // Deactivate registration
                registration.deactivate();

                // Remove from active collections
                activeRegistrations.remove(registrationId);
                eventHandlers.remove(registrationId);

                // Cancel debounce timer if exists
                ScheduledFuture<?> timer = debounceTimers.remove(directory);
                if (timer != null) {
                    timer.cancel(false);
                }

                // Close watch service if no more registrations for this directory
                boolean hasOtherRegistrations = activeRegistrations.values().stream()
                        .anyMatch(reg -> reg.getMonitoredDirectory().equals(directory));

                if (!hasOtherRegistrations) {
                    WatchService watchService = watchServices.remove(directory);
                    if (watchService != null) {
                        watchService.close();
                    }
                }

                // Update statistics
                stats.decrementActiveWatchRegistrations();

                logger.info("Stopped monitoring for directory: {}", directory);

            } catch (Exception e) {
                logger.error("Error stopping directory monitoring", e);
                stats.incrementWatchServiceErrors();
                throw new RuntimeException("Failed to stop directory monitoring", e);
            }
        }, eventProcessingExecutor);
    }

    /**
     * Creates a new WatchService instance.
     *
     * @param directory directory to create watch service for
     * @return new WatchService instance
     */
    private WatchService createWatchService(Path directory) {
        try {
            return FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            logger.error("Failed to create WatchService for directory: {}", directory, e);
            throw new RuntimeException("Failed to create WatchService", e);
        }
    }

    /**
     * Registers a directory with a watch service.
     *
     * @param watchService watch service to register with
     * @param directory    directory to register
     * @param options      registration options
     * @return watch key for the registration
     */
    private WatchKey registerDirectory(WatchService watchService, Path directory, AsyncScanOptions options) {
        try {
            // Convert string event kinds to WatchEvent.Kind
            java.util.List<WatchEvent.Kind<?>> kinds = options.getWatchEventKinds().stream()
                    .map(this::stringToWatchEventKind)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // Register with varargs - convert list to varargs call
            if (kinds.isEmpty()) {
                throw new IllegalArgumentException("No event kinds specified for registration");
            }

            // Register with varargs using reflection to handle generic types
            try {
                // Use reflection to call register with varargs
                java.lang.reflect.Method registerMethod = WatchService.class.getMethod("register",
                        Path.class, WatchEvent.Kind[].class);
                @SuppressWarnings({ "unchecked", "rawtypes" })
                WatchEvent.Kind<?>[] kindsArray = kinds.toArray(new WatchEvent.Kind[0]);
                return (WatchKey) registerMethod.invoke(watchService, directory, kindsArray);
            } catch (Exception e) {
                logger.error("Failed to register directory using reflection", e);
                throw new RuntimeException("Failed to register directory", e);
            } catch (Throwable t) {
                logger.error("Unexpected error during directory registration", t);
                throw new RuntimeException("Unexpected error during directory registration", t);
            }
        } catch (Exception e) {
            logger.error("Failed to register directory: {}", directory, e);
            throw new RuntimeException("Failed to register directory", e);
        }
    }

    /**
     * Converts string event kind to WatchEvent.Kind.
     *
     * @param eventKind string representation of event kind
     * @return WatchEvent.Kind, or null if unknown
     */
    private WatchEvent.Kind<?> stringToWatchEventKind(String eventKind) {
        switch (eventKind) {
            case "ENTRY_CREATE":
                return StandardWatchEventKinds.ENTRY_CREATE;
            case "ENTRY_MODIFY":
                return StandardWatchEventKinds.ENTRY_MODIFY;
            case "ENTRY_DELETE":
                return StandardWatchEventKinds.ENTRY_DELETE;
            case "OVERFLOW":
                return StandardWatchEventKinds.OVERFLOW;
            default:
                logger.warn("Unknown event kind: {}", eventKind);
                return null;
        }
    }

    /**
     * Main event processing loop.
     */
    private void processEventsLoop() {
        logger.debug("Starting event processing loop");

        while (running.get()) {
            try {
                // Process watch keys
                processWatchKeys();

                // Process queued events
                processQueuedEvents();

                // If no active registrations, wait a bit to avoid busy loop
                if (activeRegistrations.isEmpty()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

            } catch (Exception e) {
                logger.error("Error in event processing loop", e);
                stats.incrementWatchServiceErrors();
            }
        }

        logger.debug("Event processing loop stopped");
    }

    /**
     * Processes all active watch keys for events.
     */
    private void processWatchKeys() {
        for (WatchService watchService : watchServices.values()) {
            try {
                WatchKey watchKey = watchService.poll(100, TimeUnit.MILLISECONDS);
                if (watchKey != null) {
                    processWatchKeyEvents(watchKey);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                logger.debug("WatchService closed: {}", e.getMessage());
                break;
            } catch (Exception e) {
                logger.error("Error polling watch service", e);
                stats.incrementWatchServiceErrors();
            }
        }
    }

    /**
     * Processes events from a specific watch key.
     *
     * @param watchKey watch key to process
     */
    private void processWatchKeyEvents(WatchKey watchKey) {
        Path watchablePath = (Path) watchKey.watchable();

        for (WatchEvent<?> event : watchKey.pollEvents()) {
            try {
                processWatchEvent(event, watchablePath);
            } catch (Exception e) {
                logger.error("Error processing watch event", e);
                stats.incrementWatchServiceErrors();
            }
        }

        // Reset watch key
        boolean valid = watchKey.reset();
        if (!valid) {
            logger.warn("Watch key no longer valid for path: {}", watchablePath);
        }
    }

    /**
     * Processes a single watch event.
     *
     * @param event         watch event to process
     * @param watchablePath path being watched
     */
    private void processWatchEvent(WatchEvent<?> event, Path watchablePath) {
        WatchEvent.Kind<?> kind = event.kind();
        Path context = (Path) event.context();
        Path fullPath = watchablePath.resolve(context);

        // Convert to our event type
        FileChangeEvent.EventType eventType = watchEventKindToFileChangeEventType(kind);
        if (eventType == null) {
            logger.debug("Ignoring event kind: {}", kind);
            return;
        }

        // Create file change event
        FileChangeEvent fileChangeEvent = new FileChangeEvent(
                eventType, fullPath, findRegistrationIdForPath(watchablePath));

        // Add event to queue for processing
        if (!eventQueue.offer(fileChangeEvent)) {
            logger.warn("Event queue full, dropping event: {}", fileChangeEvent);
        }

        // Update statistics
        stats.incrementFileChangeEvents();
    }

    /**
     * Converts WatchEvent.Kind to FileChangeEvent.EventType.
     *
     * @param kind WatchEvent.Kind to convert
     * @return FileChangeEvent.EventType, or null if unknown
     */
    private FileChangeEvent.EventType watchEventKindToFileChangeEventType(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return FileChangeEvent.EventType.ENTRY_CREATE;
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return FileChangeEvent.EventType.ENTRY_MODIFY;
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return FileChangeEvent.EventType.ENTRY_DELETE;
        } else if (kind == StandardWatchEventKinds.OVERFLOW) {
            return FileChangeEvent.EventType.OVERFLOW;
        }
        return null;
    }

    /**
     * Finds registration ID for a watched path.
     *
     * @param path watched path
     * @return registration ID, or null if not found
     */
    private String findRegistrationIdForPath(Path path) {
        return activeRegistrations.values().stream()
                .filter(reg -> reg.getMonitoredDirectory().equals(path))
                .map(WatchServiceRegistration::getRegistrationId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Processes queued file change events.
     */
    private void processQueuedEvents() {
        List<FileChangeEvent> batch = new ArrayList<>();

        // Collect events for batch processing
        eventQueue.drainTo(batch, configuration.getMaxEventBatchSize());

        if (batch.isEmpty()) {
            return;
        }

        // Group events by registration ID
        Map<String, List<FileChangeEvent>> eventsByRegistration = batch.stream()
                .collect(Collectors.groupingBy(FileChangeEvent::getRegistrationId));

        // Process events for each registration
        eventsByRegistration.forEach(this::processEventsForRegistration);
    }

    /**
     * Processes events for a specific registration.
     *
     * @param registrationId registration ID
     * @param events         list of events to process
     */
    private void processEventsForRegistration(String registrationId, List<FileChangeEvent> events) {
        Consumer<FileChangeEvent> eventHandler = eventHandlers.get(registrationId);
        if (eventHandler == null) {
            logger.warn("No event handler found for registration: {}", registrationId);
            return;
        }

        WatchServiceRegistration registration = activeRegistrations.get(registrationId);
        if (registration == null || !registration.isActive()) {
            logger.debug("Registration not active: {}", registrationId);
            return;
        }

        // Apply debouncing if enabled
        if (configuration.isEventDebouncingEnabled()) {
            processEventsWithDebouncing(registration, events, eventHandler);
        } else {
            // Process events directly
            events.forEach(event -> {
                registration.incrementEventsProcessed();
                registration.updateLastEventTime();
                eventHandler.accept(event);
            });
        }
    }

    /**
     * Processes events with debouncing logic.
     *
     * @param registration watch service registration
     * @param events       events to process
     * @param eventHandler event handler
     */
    private void processEventsWithDebouncing(WatchServiceRegistration registration,
            List<FileChangeEvent> events,
            Consumer<FileChangeEvent> eventHandler) {
        Path directory = registration.getMonitoredDirectory();

        // Cancel existing debounce timer
        ScheduledFuture<?> existingTimer = debounceTimers.get(directory);
        if (existingTimer != null) {
            existingTimer.cancel(false);
        }

        // Schedule new debounce timer
        ScheduledFuture<?> timer = Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> {
                    // Process all events after debounce timeout
                    events.forEach(event -> {
                        registration.incrementEventsProcessed();
                        registration.updateLastEventTime();
                        eventHandler.accept(event);
                    });

                    // Remove timer from map
                    debounceTimers.remove(directory);

                }, configuration.getDebounceTimeoutMs(), TimeUnit.MILLISECONDS);

        debounceTimers.put(directory, timer);
    }

    /**
     * Gets current statistics.
     *
     * @return scanner statistics
     */
    public AsyncScannerStats getStats() {
        return stats;
    }

    /**
     * Checks if the manager is running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the number of active registrations.
     *
     * @return active registration count
     */
    public int getActiveRegistrationCount() {
        return activeRegistrations.size();
    }

    /**
     * Gets all active registrations.
     *
     * @return copy of active registrations
     */
    public Map<String, WatchServiceRegistration> getActiveRegistrations() {
        return new HashMap<>(activeRegistrations);
    }
}