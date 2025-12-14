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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.scanner;

/**
 * Enumeration of batch processing priority levels.
 * Controls the order and resource allocation for batch operations.
 */
public enum BatchPriority {

    /**
     * Critical priority - processed immediately with maximum resources.
     * Used for emergency operations and system-critical files.
     */
    CRITICAL(1, "Critical", "Processed immediately with maximum resources"),

    /**
     * High priority - processed before normal priority batches.
     * Used for important user operations and time-sensitive files.
     */
    HIGH(2, "High", "Processed before normal priority batches"),

    /**
     * Normal priority - standard processing order.
     * Used for regular backup operations and routine processing.
     */
    NORMAL(3, "Normal", "Standard processing order"),

    /**
     * Low priority - processed when system resources are available.
     * Used for background operations and non-urgent tasks.
     */
    LOW(4, "Low", "Processed when system resources are available"),

    /**
     * Background priority - processed only during idle periods.
     * Used for maintenance tasks and non-time-critical operations.
     */
    BACKGROUND(5, "Background", "Processed only during idle periods");

    private final int level;
    private final String name;
    private final String description;

    /**
     * Creates a new BatchPriority.
     *
     * @param level priority level (lower = higher priority)
     * @param name priority name
     * @param description priority description
     */
    BatchPriority(int level, String name, String description) {
        this.level = level;
        this.name = name;
        this.description = description;
    }

    /**
     * Gets the priority level.
     *
     * @return priority level (lower = higher priority)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Gets the priority name.
     *
     * @return priority name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the priority description.
     *
     * @return priority description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this priority is higher than another priority.
     *
     * @param other the other priority to compare
     * @return true if this priority is higher
     */
    public boolean isHigherThan(BatchPriority other) {
        return this.level < other.level;
    }

    /**
     * Checks if this priority is lower than another priority.
     *
     * @param other the other priority to compare
     * @return true if this priority is lower
     */
    public boolean isLowerThan(BatchPriority other) {
        return this.level > other.level;
    }

    @Override
    public String toString() {
        return name;
    }
}