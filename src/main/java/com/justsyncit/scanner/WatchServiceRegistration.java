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

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a registration for directory monitoring with WatchService.
 * Contains metadata about the watch service and provides methods for
 * management.
 */
public class WatchServiceRegistration {

    /** Unique identifier for this registration. */
    private final String registrationId;

    /** Directory being monitored. */
    private final Path monitoredDirectory;

    /** Types of events being watched. */
    private final Set<String> watchEventKinds;

    /** Whether monitoring is recursive (includes subdirectories). */
    private final boolean recursiveWatching;

    /** Registration timestamp. */
    private final Instant registrationTime;

    /** Watch service configuration. */
    private final AsyncScanOptions configuration;

    /** Atomic flag indicating if registration is active. */
    private final AtomicBoolean active;

    /** Counter for events processed. */
    private final AtomicLong eventsProcessed;

    /** Counter for errors encountered. */
    private final AtomicLong errorsEncountered;

    /** Last event timestamp. */
    private volatile Instant lastEventTime;

    /** Additional metadata. */
    private volatile Object metadata;

    /**
     * Creates a new WatchServiceRegistration.
     *
     * @param monitoredDirectory directory being monitored
     * @param watchEventKinds    types of events being watched
     * @param recursiveWatching  whether monitoring is recursive
     * @param configuration      watch service configuration
     */
    public WatchServiceRegistration(Path monitoredDirectory, Set<String> watchEventKinds,
            boolean recursiveWatching, AsyncScanOptions configuration) {
        this.registrationId = UUID.randomUUID().toString();
        this.monitoredDirectory = monitoredDirectory;
        this.watchEventKinds = Set.copyOf(watchEventKinds);
        this.recursiveWatching = recursiveWatching;
        this.configuration = configuration;
        this.registrationTime = Instant.now();
        this.active = new AtomicBoolean(true);
        this.eventsProcessed = new AtomicLong(0);
        this.errorsEncountered = new AtomicLong(0);
    }

    /**
     * Gets the unique identifier for this registration.
     *
     * @return registration ID
     */
    public String getRegistrationId() {
        return registrationId;
    }

    /**
     * Gets the directory being monitored.
     *
     * @return monitored directory
     */
    public Path getMonitoredDirectory() {
        return monitoredDirectory;
    }

    /**
     * Gets the types of events being watched.
     *
     * @return watch event kinds
     */
    public Set<String> getWatchEventKinds() {
        return watchEventKinds;
    }

    /**
     * Checks if monitoring is recursive (includes subdirectories).
     *
     * @return true if recursive, false otherwise
     */
    public boolean isRecursiveWatching() {
        return recursiveWatching;
    }

    /**
     * Gets the registration timestamp.
     *
     * @return registration time
     */
    public Instant getRegistrationTime() {
        return registrationTime;
    }

    /**
     * Gets the watch service configuration.
     *
     * @return configuration
     */
    public AsyncScanOptions getConfiguration() {
        return configuration;
    }

    /**
     * Checks if the registration is currently active.
     *
     * @return true if active, false otherwise
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Deactivates this registration.
     *
     * @return true if was active and is now deactivated, false if already inactive
     */
    public boolean deactivate() {
        return active.compareAndSet(true, false);
    }

    /**
     * Gets the number of events processed so far.
     *
     * @return events processed count
     */
    public long getEventsProcessed() {
        return eventsProcessed.get();
    }

    /**
     * Increments the events processed counter.
     *
     * @return the new count
     */
    public long incrementEventsProcessed() {
        return eventsProcessed.incrementAndGet();
    }

    /**
     * Gets the number of errors encountered so far.
     *
     * @return errors encountered count
     */
    public long getErrorsEncountered() {
        return errorsEncountered.get();
    }

    /**
     * Increments the errors encountered counter.
     *
     * @return the new count
     */
    public long incrementErrorsEncountered() {
        return errorsEncountered.incrementAndGet();
    }

    /**
     * Gets the timestamp of the last event processed.
     *
     * @return last event time, or null if no events processed yet
     */
    public Instant getLastEventTime() {
        return lastEventTime;
    }

    /**
     * Updates the last event timestamp to current time.
     */
    public void updateLastEventTime() {
        this.lastEventTime = Instant.now();
    }

    /**
     * Gets the duration since registration.
     *
     * @return duration in milliseconds
     */
    public long getRegistrationDurationMs() {
        return java.time.Duration.between(registrationTime, Instant.now()).toMillis();
    }

    /**
     * Gets the average event rate (events per second).
     *
     * @return average event rate
     */
    public double getAverageEventRate() {
        long durationMs = getRegistrationDurationMs();
        if (durationMs == 0) {
            return 0.0;
        }
        return (eventsProcessed.get() * 1000.0) / durationMs;
    }

    /**
     * Gets additional metadata.
     *
     * @return metadata object
     */
    public Object getMetadata() {
        return metadata;
    }

    /**
     * Sets additional metadata.
     *
     * @param metadata metadata object
     */
    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    /**
     * Asynchronously stops this registration and cleans up resources.
     *
     * @return a CompletableFuture that completes when cleanup is done
     */
    public CompletableFuture<Void> stopAsync() {
        return CompletableFuture.runAsync(() -> {
            deactivate();
            // Additional cleanup logic can be added here
        });
    }

    /**
     * Creates a summary of this registration.
     *
     * @return summary string
     */
    public String createSummary() {
        return String.format(
                "WatchServiceRegistration{id='%s', directory='%s', events=%d, errors=%d, "
                        + "duration=%dms, rate=%.2f events/sec, active=%b}",
                registrationId, monitoredDirectory, eventsProcessed.get(), errorsEncountered.get(),
                getRegistrationDurationMs(), getAverageEventRate(), isActive());
    }

    @Override
    public String toString() {
        return createSummary();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        WatchServiceRegistration that = (WatchServiceRegistration) obj;
        return registrationId.equals(that.registrationId);
    }

    @Override
    public int hashCode() {
        return registrationId.hashCode();
    }
}