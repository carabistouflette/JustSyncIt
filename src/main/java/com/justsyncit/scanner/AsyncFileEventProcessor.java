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

import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Processes file change events asynchronously with batching, debouncing, and
 * filtering capabilities.
 * Provides high-performance event processing for real-time file system
 * monitoring.
 */
public class AsyncFileEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AsyncFileEventProcessor.class);

    /** Thread pool for event processing. */
    private final ExecutorService eventProcessorPool;

    /** Queue for batching events. */
    private final BlockingQueue<FileChangeEvent> eventQueue;

    /** Event handler for processed events. */
    private final Consumer<FileChangeEvent> eventHandler;

    /** Event filters to apply before processing. */
    private final List<Predicate<FileChangeEvent>> eventFilters;

    /** Event matcher for pattern-based filtering. */
    private PathMatcher eventMatcher;

    /** Debounce delay in milliseconds. */
    private final long debounceDelayMs;

    /** Batch size for event processing. */
    private final int batchSize;

    /** Maximum queue size. */
    private final int maxQueueSize;

    /** Processor state. */
    private final AtomicBoolean running;

    /** Statistics tracking. */
    private final EventProcessorStats stats;

    /** Debounce map for tracking recent events. */
    private final Map<String, Instant> debounceMap;

    /** Scheduled executor for debounce cleanup. */
    private final ScheduledExecutorService debounceExecutor;

    /**
     * Statistics for event processing.
     */
    public static class EventProcessorStats {
        private final AtomicLong totalEventsProcessed = new AtomicLong(0);
        private final AtomicLong eventsFiltered = new AtomicLong(0);
        private final AtomicLong eventsBatched = new AtomicLong(0);
        private final AtomicLong eventsDebounced = new AtomicLong(0);
        private final AtomicLong processingErrors = new AtomicLong(0);
        private volatile Instant lastProcessedTime = Instant.now();

        public long getTotalEventsProcessed() {
            return totalEventsProcessed.get();
        }

        public long getEventsFiltered() {
            return eventsFiltered.get();
        }

        public long getEventsBatched() {
            return eventsBatched.get();
        }

        public long getEventsDebounced() {
            return eventsDebounced.get();
        }

        public long getProcessingErrors() {
            return processingErrors.get();
        }

        public Instant getLastProcessedTime() {
            return lastProcessedTime;
        }

        void incrementTotalProcessed() {
            totalEventsProcessed.incrementAndGet();
            lastProcessedTime = Instant.now();
        }

        void incrementFiltered() {
            eventsFiltered.incrementAndGet();
        }

        void incrementBatched(int count) {
            eventsBatched.addAndGet(count);
        }

        void incrementDebounced() {
            eventsDebounced.incrementAndGet();
        }

        void incrementErrors() {
            processingErrors.incrementAndGet();
        }

        @Override
        public String toString() {
            return String.format(
                    "EventProcessorStats{processed=%d, filtered=%d, batched=%d, debounced=%d, errors=%d, lastProcessed=%s}",
                    getTotalEventsProcessed(), getEventsFiltered(), getEventsBatched(),
                    getEventsDebounced(), getProcessingErrors(), getLastProcessedTime());
        }
    }

    /**
     * Configuration for event processing.
     */
    public static class EventProcessorConfig {
        private int threadPoolSize = Runtime.getRuntime().availableProcessors();
        private int queueSize = 10000;
        private int batchSize = 100;
        private long debounceDelayMs = 100;
        private PathMatcher eventMatcher;
        private List<Predicate<FileChangeEvent>> eventFilters = new ArrayList<>();

        public EventProcessorConfig withThreadPoolSize(int size) {
            this.threadPoolSize = Math.max(1, size);
            return this;
        }

        public EventProcessorConfig withQueueSize(int size) {
            this.queueSize = Math.max(100, size);
            return this;
        }

        public EventProcessorConfig withBatchSize(int size) {
            this.batchSize = Math.max(1, size);
            return this;
        }

        public EventProcessorConfig withDebounceDelay(long delayMs) {
            this.debounceDelayMs = Math.max(0, delayMs);
            return this;
        }

        public EventProcessorConfig withEventMatcher(PathMatcher matcher) {
            this.eventMatcher = matcher;
            return this;
        }

        public EventProcessorConfig withEventFilter(Predicate<FileChangeEvent> filter) {
            this.eventFilters.add(filter);
            return this;
        }

        // Getters
        public int getThreadPoolSize() {
            return threadPoolSize;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public long getDebounceDelayMs() {
            return debounceDelayMs;
        }

        public PathMatcher getEventMatcher() {
            return eventMatcher;
        }

        public List<Predicate<FileChangeEvent>> getEventFilters() {
            return new ArrayList<>(eventFilters);
        }
    }

    /**
     * Creates a new AsyncFileEventProcessor with default configuration.
     *
     * @param eventHandler the event handler to call for processed events
     */
    public AsyncFileEventProcessor(Consumer<FileChangeEvent> eventHandler) {
        this(new EventProcessorConfig(), eventHandler);
    }

    /**
     * Creates a new AsyncFileEventProcessor with custom configuration.
     *
     * @param config       the processor configuration
     * @param eventHandler the event handler to call for processed events
     */
    public AsyncFileEventProcessor(EventProcessorConfig config, Consumer<FileChangeEvent> eventHandler) {
        this.eventHandler = Objects.requireNonNull(eventHandler, "Event handler cannot be null");
        this.eventProcessorPool = Executors.newFixedThreadPool(config.getThreadPoolSize());
        this.eventQueue = new LinkedBlockingQueue<>(config.getQueueSize());
        this.eventFilters = new ArrayList<>(config.getEventFilters());
        this.eventMatcher = config.getEventMatcher();
        this.debounceDelayMs = config.getDebounceDelayMs();
        this.batchSize = config.getBatchSize();
        this.maxQueueSize = config.getQueueSize();
        this.running = new AtomicBoolean(false);
        this.stats = new EventProcessorStats();
        this.debounceMap = new ConcurrentHashMap<>();
        this.debounceExecutor = Executors.newSingleThreadScheduledExecutor();

        logger.info(
                "AsyncFileEventProcessor initialized with config: threads={}, queueSize={}, batchSize={}, debounceMs={}",
                config.getThreadPoolSize(), config.getQueueSize(), config.getBatchSize(), config.getDebounceDelayMs());
    }

    /**
     * Starts the event processor.
     *
     * @return a CompletableFuture that completes when the processor is started
     */
    public CompletableFuture<Void> startAsync() {
        return CompletableFuture.runAsync(() -> {
            if (!running.compareAndSet(false, true)) {
                logger.warn("Event processor is already running");
                return;
            }

            logger.info("Starting AsyncFileEventProcessor");

            // Start event processing loop
            eventProcessorPool.submit(this::processEventLoop);

            // Start debounce cleanup task
            if (debounceDelayMs > 0) {
                debounceExecutor.scheduleAtFixedRate(
                        this::cleanupDebounceMap,
                        debounceDelayMs,
                        debounceDelayMs,
                        TimeUnit.MILLISECONDS);
            }

            logger.info("AsyncFileEventProcessor started successfully");
        }, eventProcessorPool);
    }

    /**
     * Stops the event processor.
     *
     * @return a CompletableFuture that completes when the processor is stopped
     */
    public CompletableFuture<Void> stopAsync() {
        return CompletableFuture.runAsync(() -> {
            if (!running.compareAndSet(true, false)) {
                logger.warn("Event processor is not running");
                return;
            }

            logger.info("Stopping AsyncFileEventProcessor");

            try {
                // Shutdown debounce executor
                debounceExecutor.shutdown();
                if (!debounceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    debounceExecutor.shutdownNow();
                }

                // Shutdown event processor pool
                eventProcessorPool.shutdown();
                if (!eventProcessorPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    eventProcessorPool.shutdownNow();
                }

                // Process remaining events
                processRemainingEvents();

                logger.info("AsyncFileEventProcessor stopped successfully");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while stopping event processor", e);
            }
        }, eventProcessorPool);
    }

    /**
     * Processes a file change event asynchronously.
     *
     * @param event the file change event
     * @return a CompletableFuture that completes when the event is queued
     */
    public CompletableFuture<Void> processEventAsync(FileChangeEvent event) {
        return CompletableFuture.runAsync(() -> {
            if (!running.get()) {
                logger.debug("Event processor is not running, ignoring event: {}", event);
                return;
            }

            try {
                if (!eventQueue.offer(event)) {
                    logger.warn("Event queue is full, dropping event: {}", event);
                    stats.incrementErrors();
                    return;
                }

                logger.debug("Event queued: {}", event);

            } catch (Exception e) {
                logger.error("Error queuing event: {}", event, e);
                stats.incrementErrors();
            }
        }, eventProcessorPool);
    }

    /**
     * Processes multiple events in batch.
     *
     * @param events the events to process
     * @return a CompletableFuture that completes when all events are queued
     */
    public CompletableFuture<Void> processEventsAsync(List<FileChangeEvent> events) {
        if (events == null || events.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            if (!running.get()) {
                logger.debug("Event processor is not running, ignoring {} events", events.size());
                return;
            }

            int queuedCount = 0;
            for (FileChangeEvent event : events) {
                if (eventQueue.offer(event)) {
                    queuedCount++;
                } else {
                    logger.warn("Event queue is full, dropping event: {}", event);
                    stats.incrementErrors();
                }
            }

            logger.debug("Queued {} events out of {}", queuedCount, events.size());
            stats.incrementBatched(queuedCount);

        }, eventProcessorPool);
    }

    /**
     * Gets the current processor statistics.
     *
     * @return the processor statistics
     */
    public EventProcessorStats getStats() {
        return stats;
    }

    /**
     * Checks if the processor is running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the current queue size.
     *
     * @return the number of events in the queue
     */
    public int getQueueSize() {
        return eventQueue.size();
    }

    /**
     * Main event processing loop.
     */
    private void processEventLoop() {
        List<FileChangeEvent> batch = new ArrayList<>(batchSize);

        while (running.get()) {
            try {
                // Collect events for batch processing
                FileChangeEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    batch.add(event);

                    // Collect more events for batch
                    while (batch.size() < batchSize && !eventQueue.isEmpty()) {
                        FileChangeEvent additionalEvent = eventQueue.poll();
                        if (additionalEvent != null) {
                            batch.add(additionalEvent);
                        }
                    }
                }

                // Process the batch
                if (!batch.isEmpty()) {
                    processBatch(new ArrayList<>(batch));
                    batch.clear();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Event processing loop interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error in event processing loop", e);
                stats.incrementErrors();
            }
        }
    }

    /**
     * Processes a batch of events.
     *
     * @param events the events to process
     */
    private void processBatch(List<FileChangeEvent> events) {
        try {
            // Apply filters and debouncing
            List<FileChangeEvent> processedEvents = new ArrayList<>();

            for (FileChangeEvent event : events) {
                if (shouldProcessEvent(event)) {
                    processedEvents.add(event);
                } else {
                    stats.incrementFiltered();
                }
            }

            // Call event handler for each processed event
            for (FileChangeEvent event : processedEvents) {
                try {
                    eventHandler.accept(event);
                    stats.incrementTotalProcessed();
                } catch (Exception e) {
                    logger.error("Error in event handler for: {}", event, e);
                    stats.incrementErrors();
                }
            }

            logger.debug("Processed batch of {} events ({} filtered)",
                    events.size(), events.size() - processedEvents.size());

        } catch (Exception e) {
            logger.error("Error processing event batch", e);
            stats.incrementErrors();
        }
    }

    /**
     * Determines if an event should be processed.
     *
     * @param event the event to check
     * @return true if the event should be processed
     */
    private boolean shouldProcessEvent(FileChangeEvent event) {
        // Apply pattern matcher
        if (eventMatcher != null && !eventMatcher.matches(event.getFilePath())) {
            return false;
        }

        // Apply custom filters
        for (Predicate<FileChangeEvent> filter : eventFilters) {
            try {
                if (!filter.test(event)) {
                    return false;
                }
            } catch (Exception e) {
                logger.warn("Error in event filter for: {}", event, e);
                return false;
            }
        }

        // Apply debouncing
        if (debounceDelayMs > 0) {
            String eventKey = createEventKey(event);
            Instant now = Instant.now();

            synchronized (debounceMap) {
                Instant lastEventTime = debounceMap.get(eventKey);
                if (lastEventTime != null
                        && now.toEpochMilli() - lastEventTime.toEpochMilli() < debounceDelayMs) {
                    stats.incrementDebounced();
                    return false;
                }
                debounceMap.put(eventKey, now);
            }
        }

        return true;
    }

    /**
     * Creates a unique key for debouncing events.
     *
     * @param event the event
     * @return the event key
     */
    private String createEventKey(FileChangeEvent event) {
        return String.format("%s:%s:%s",
                event.getEventType(),
                event.getFilePath().toString(),
                event.getFileSize() // Include size to detect file modifications
        );
    }

    /**
     * Cleans up expired entries from the debounce map.
     */
    private void cleanupDebounceMap() {
        if (debounceMap.isEmpty()) {
            return;
        }

        Instant cutoff = Instant.now().minusMillis(debounceDelayMs);
        synchronized (debounceMap) {
            debounceMap.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        }
    }

    /**
     * Processes remaining events in the queue during shutdown.
     */
    private void processRemainingEvents() {
        List<FileChangeEvent> remainingEvents = new ArrayList<>();
        eventQueue.drainTo(remainingEvents);

        if (!remainingEvents.isEmpty()) {
            logger.info("Processing {} remaining events during shutdown", remainingEvents.size());
            processBatch(remainingEvents);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "AsyncFileEventProcessor{running=%b, queueSize=%d, stats=%s}",
                running.get(), getQueueSize(), stats);
    }
}