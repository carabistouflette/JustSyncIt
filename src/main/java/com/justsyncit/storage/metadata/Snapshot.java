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

package com.justsyncit.storage.metadata;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a backup snapshot containing metadata about the backup point in time.
 */
public final class Snapshot {

    /** Unique identifier for the snapshot. */
    private final String id;
    /** Human-readable name for the snapshot. */
    private final String name;
    /** Description of the snapshot. */
    private final String description;
    /** Timestamp when the snapshot was created. */
    private final Instant createdAt;
    /** Total number of files in the snapshot. */
    private final long totalFiles;
    /** Total size of all files in the snapshot. */
    private final long totalSize;

    /**
     * Creates a new Snapshot instance.
     *
     * @param id unique identifier for the snapshot
     * @param name human-readable name for the snapshot
     * @param description description of the snapshot
     * @param createdAt timestamp when the snapshot was created
     * @param totalFiles total number of files in the snapshot
     * @param totalSize total size of all files in the snapshot
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    public Snapshot(String id, String name, String description, Instant createdAt,
                   long totalFiles, long totalSize) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Snapshot name cannot be null or empty");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created timestamp cannot be null");
        }
        if (totalFiles < 0) {
            throw new IllegalArgumentException("Total files cannot be negative");
        }
        if (totalSize < 0) {
            throw new IllegalArgumentException("Total size cannot be negative");
        }

        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.totalFiles = totalFiles;
        this.totalSize = totalSize;
    }

    /**
     * Gets the unique identifier for the snapshot.
     *
     * @return the snapshot ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the human-readable name for the snapshot.
     *
     * @return the snapshot name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the description of the snapshot.
     *
     * @return the snapshot description, or null if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the timestamp when the snapshot was created.
     *
     * @return the creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the total number of files in the snapshot.
     *
     * @return the total file count
     */
    public long getTotalFiles() {
        return totalFiles;
    }

    /**
     * Gets the total size of all files in the snapshot.
     *
     * @return the total size in bytes
     */
    public long getTotalSize() {
        return totalSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Snapshot snapshot = (Snapshot) o;
        return Objects.equals(id, snapshot.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Snapshot{"
                + "id='" + id + '\''
                + ", name='" + name + '\''
                + ", description='" + description + '\''
                + ", createdAt=" + createdAt
                + ", totalFiles=" + totalFiles
                + ", totalSize=" + totalSize
                + '}';
    }
}