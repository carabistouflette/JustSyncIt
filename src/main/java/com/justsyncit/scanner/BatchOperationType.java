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
 * Enumeration of batch operation types for different processing scenarios.
 * Defines the specific operations that can be performed on file batches.
 */
public enum BatchOperationType {

    /**
     * Chunking operation - breaks files into chunks.
     * Used for backup operations and content analysis.
     */
    CHUNKING("Chunking", "Breaks files into chunks for backup operations"),

    /**
     * Hashing operation - calculates file and chunk hashes.
     * Used for integrity verification and deduplication.
     */
    HASHING("Hashing", "Calculates file and chunk hashes for integrity verification"),

    /**
     * Storage operation - stores chunks to content store.
     * Used for persisting backup data and metadata.
     */
    STORAGE("Storage", "Stores chunks to content store for backup persistence"),

    /**
     * Transfer operation - transfers files between locations.
     * Used for network transfers and migration operations.
     */
    TRANSFER("Transfer", "Transfers files between locations for network operations"),

    /**
     * Verification operation - verifies file integrity.
     * Used for backup validation and consistency checks.
     */
    VERIFICATION("Verification", "Verifies file integrity for backup validation"),

    /**
     * Compression operation - compresses files for storage.
     * Used for space optimization and faster transfers.
     */
    COMPRESSION("Compression", "Compresses files for storage optimization"),

    /**
     * Deduplication operation - removes duplicate content.
     * Used for storage efficiency and optimization.
     */
    DEDUPLICATION("Deduplication", "Removes duplicate content for storage efficiency"),

    /**
     * Metadata operation - processes file metadata.
     * Used for catalog operations and indexing.
     */
    METADATA("Metadata", "Processes file metadata for catalog operations"),

    /**
     * Recovery operation - recovers from backup.
     * Used for restore operations and disaster recovery.
     */
    RECOVERY("Recovery", "Recovers from backup for restore operations"),

    /**
     * Maintenance operation - performs system maintenance.
     * Used for cleanup and optimization tasks.
     */
    MAINTENANCE("Maintenance", "Performs system maintenance for cleanup and optimization");

    private final String name;
    private final String description;

    /**
     * Creates a new BatchOperationType.
     *
     * @param name        operation type name
     * @param description operation type description
     */
    BatchOperationType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Gets the operation type name.
     *
     * @return operation type name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the operation type description.
     *
     * @return operation type description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this operation type is I/O intensive.
     *
     * @return true if I/O intensive, false otherwise
     */
    public boolean isIOIntensive() {
        return this == CHUNKING || this == STORAGE || this == TRANSFER
                || this == COMPRESSION || this == RECOVERY;
    }

    /**
     * Checks if this operation type is CPU intensive.
     *
     * @return true if CPU intensive, false otherwise
     */
    public boolean isCPUIntensive() {
        return this == HASHING || this == COMPRESSION || this == DEDUPLICATION;
    }

    /**
     * Checks if this operation type is memory intensive.
     *
     * @return true if memory intensive, false otherwise
     */
    public boolean isMemoryIntensive() {
        return this == CHUNKING || this == COMPRESSION || this == DEDUPLICATION;
    }

    /**
     * Gets the recommended thread pool type for this operation.
     *
     * @return recommended thread pool type
     */
    public ThreadPoolManager.PoolType getRecommendedThreadPoolType() {
        switch (this) {
            case CHUNKING:
            case STORAGE:
            case TRANSFER:
            case RECOVERY:
                return ThreadPoolManager.PoolType.IO;
            case HASHING:
            case COMPRESSION:
            case DEDUPLICATION:
                return ThreadPoolManager.PoolType.CPU;
            case VERIFICATION:
            case METADATA:
            case MAINTENANCE:
                return ThreadPoolManager.PoolType.MANAGEMENT;
            default:
                return ThreadPoolManager.PoolType.BATCH_PROCESSING;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}