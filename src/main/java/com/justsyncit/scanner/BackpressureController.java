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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Controls backpressure for async directory scanning operations.
 * Provides flow control to prevent resource exhaustion and maintain system stability.
 */
public class BackpressureController {

    private static final Logger logger = LoggerFactory.getLogger(BackpressureController.class);

    /** Current backpressure level (0.0 to 1.0). */
    private final AtomicReference<Double> currentPressureLevel;
    
    /** Whether backpressure is currently applied. */
    private final AtomicBoolean backpressureApplied;
    
    /** Total number of backpressure events applied. */
    private final AtomicLong totalBackpressureEvents;
    
    /** Timestamp when backpressure was last applied. */
    private volatile long lastBackpressureTime;
    
    /** Lock for thread-safe operations. */
    private final ReentrantReadWriteLock lock;

    /**
     * Creates a new BackpressureController.
     */
    public BackpressureController() {
        this.currentPressureLevel = new AtomicReference<>(0.0);
        this.backpressureApplied = new AtomicBoolean(false);
        this.totalBackpressureEvents = new AtomicLong(0);
        this.lastBackpressureTime = System.currentTimeMillis();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Applies backpressure at the specified level.
     *
     * @param pressureLevel the backpressure level (0.0 to 1.0)
     */
    public void applyBackpressure(double pressureLevel) {
        if (pressureLevel < 0.0 || pressureLevel > 1.0) {
            throw new IllegalArgumentException("Pressure level must be between 0.0 and 1.0");
        }

        lock.writeLock().lock();
        try {
            double previousLevel = currentPressureLevel.get();
            currentPressureLevel.set(pressureLevel);
            
            if (pressureLevel > 0.0 && !backpressureApplied.get()) {
                backpressureApplied.set(true);
                totalBackpressureEvents.incrementAndGet();
                lastBackpressureTime = System.currentTimeMillis();
                
                logger.info("Applied backpressure at level: {:.2f} (previous: {:.2f})", 
                    pressureLevel, previousLevel);
            } else if (pressureLevel == 0.0 && backpressureApplied.get()) {
                backpressureApplied.set(false);
                lastBackpressureTime = System.currentTimeMillis();
                
                logger.info("Released backpressure (previous level: {:.2f})", previousLevel);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Releases all backpressure.
     */
    public void releaseBackpressure() {
        applyBackpressure(0.0);
    }

    /**
     * Gets the current backpressure level.
     *
     * @return current pressure level (0.0 to 1.0)
     */
    public double getCurrentPressureLevel() {
        return currentPressureLevel.get();
    }

    /**
     * Checks if backpressure is currently applied.
     *
     * @return true if backpressure is applied, false otherwise
     */
    public boolean isBackpressureApplied() {
        return backpressureApplied.get();
    }

    /**
     * Gets the total number of backpressure events applied.
     *
     * @return total backpressure events
     */
    public long getTotalBackpressureEvents() {
        return totalBackpressureEvents.get();
    }

    /**
     * Gets the timestamp when backpressure was last applied.
     *
     * @return last backpressure time in milliseconds
     */
    public long getLastBackpressureTime() {
        return lastBackpressureTime;
    }

    /**
     * Gets the duration since backpressure was last applied.
     *
     * @return duration in milliseconds, or -1 if never applied
     */
    public long getTimeSinceLastBackpressure() {
        if (lastBackpressureTime == 0) {
            return -1;
        }
        return System.currentTimeMillis() - lastBackpressureTime;
    }

    /**
     * Checks if the system is under backpressure.
     *
     * @param threshold the threshold to check against
     * @return true if pressure level exceeds threshold
     */
    public boolean isUnderBackpressure(double threshold) {
        return getCurrentPressureLevel() > threshold;
    }

    /**
     * Gets a summary of the current backpressure state.
     *
     * @return summary string
     */
    public String getSummary() {
        return String.format(
            "BackpressureController{level=%.2f, applied=%b, totalEvents=%d, lastEvent=%dms ago}",
            getCurrentPressureLevel(), isBackpressureApplied(), getTotalBackpressureEvents(),
            getTimeSinceLastBackpressure()
        );
    }

    @Override
    public String toString() {
        return getSummary();
    }
}