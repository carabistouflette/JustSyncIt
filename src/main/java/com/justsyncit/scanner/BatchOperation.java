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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a batch operation with metadata and dependencies.
 * Provides comprehensive tracking of batch processing operations
 * with support for dependencies, priorities, and resource allocation.
 */
public class BatchOperation {

    /** Unique identifier for this batch operation. */
    private final String operationId;

    /** Type of batch operation. */
    private final BatchOperationType operationType;

    /** Files to be processed in this batch. */
    private final List<Path> files;

    /** Priority level for this operation. */
    private final BatchPriority priority;

    /** Timestamp when operation was created. */
    private final Instant createdTime;

    /** Estimated resource requirements. */
    private final ResourceRequirements resourceRequirements;

    /** Operation dependencies that must complete first. */
    private final Map<String, BatchOperation> dependencies;

    /** Operation metadata. */
    private final Map<String, Object> metadata;

    /**
     * Creates a new BatchOperation.
     *
     * @param operationId unique identifier for this operation
     * @param operationType type of batch operation
     * @param files files to be processed
     * @param priority priority level for this operation
     * @param resourceRequirements estimated resource requirements
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    public BatchOperation(String operationId, BatchOperationType operationType, List<Path> files,
                     BatchPriority priority, ResourceRequirements resourceRequirements) {
        if (operationId == null || operationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Operation ID cannot be null or empty");
        }
        if (operationType == null) {
            throw new IllegalArgumentException("Operation type cannot be null");
        }
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be null or empty");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Priority cannot be null");
        }
        if (resourceRequirements == null) {
            throw new IllegalArgumentException("Resource requirements cannot be null");
        }

        this.operationId = operationId;
        this.operationType = operationType;
        this.files = new java.util.ArrayList<>(files);
        this.priority = priority;
        this.createdTime = Instant.now();
        this.resourceRequirements = resourceRequirements;
        this.dependencies = new ConcurrentHashMap<>();
        this.metadata = new ConcurrentHashMap<>();
    }

    /**
     * Gets the unique operation identifier.
     *
     * @return operation ID
     */
    public String getOperationId() {
        return operationId;
    }

    /**
     * Gets the operation type.
     *
     * @return operation type
     */
    public BatchOperationType getOperationType() {
        return operationType;
    }

    /**
     * Gets the list of files to be processed.
     *
     * @return immutable list of files
     */
    public List<Path> getFiles() {
        return new java.util.ArrayList<>(files);
    }

    /**
     * Gets the priority level.
     *
     * @return priority level
     */
    public BatchPriority getPriority() {
        return priority;
    }

    /**
     * Gets the creation timestamp.
     *
     * @return creation time
     */
    public Instant getCreatedTime() {
        return createdTime;
    }

    /**
     * Gets the resource requirements.
     *
     * @return resource requirements
     */
    public ResourceRequirements getResourceRequirements() {
        return resourceRequirements;
    }

    /**
     * Gets the operation dependencies.
     *
     * @return immutable map of dependencies
     */
    public Map<String, BatchOperation> getDependencies() {
        return new java.util.HashMap<>(dependencies);
    }

    /**
     * Adds a dependency to this operation.
     *
     * @param dependencyId ID of the dependency operation
     * @param dependency the dependency operation
     * @throws IllegalArgumentException if dependencyId is null or empty, or dependency is null
     */
    public void addDependency(String dependencyId, BatchOperation dependency) {
        if (dependencyId == null || dependencyId.trim().isEmpty()) {
            throw new IllegalArgumentException("Dependency ID cannot be null or empty");
        }
        if (dependency == null) {
            throw new IllegalArgumentException("Dependency cannot be null");
        }
        dependencies.put(dependencyId, dependency);
    }

    /**
     * Removes a dependency from this operation.
     *
     * @param dependencyId ID of the dependency to remove
     * @return true if dependency was removed, false if not found
     */
    public boolean removeDependency(String dependencyId) {
        return dependencies.remove(dependencyId) != null;
    }

    /**
     * Checks if this operation has dependencies.
     *
     * @return true if there are dependencies, false otherwise
     */
    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    /**
     * Gets the operation metadata.
     *
     * @return immutable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return new java.util.HashMap<>(metadata);
    }

    /**
     * Sets metadata for this operation.
     *
     * @param key metadata key
     * @param value metadata value
     * @throws IllegalArgumentException if key is null or empty
     */
    public void setMetadata(String key, Object value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Metadata key cannot be null or empty");
        }
        metadata.put(key, value);
    }

    /**
     * Gets metadata value for a key.
     *
     * @param key metadata key
     * @return metadata value, or null if not found
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Gets the total size of all files in this operation.
     *
     * @return total size in bytes
     */
    public long getTotalFileSize() {
        return files.stream()
                .mapToLong(file -> {
                    try {
                        return java.nio.file.Files.size(file);
                    } catch (java.io.IOException e) {
                        return 0L;
                    }
                })
                .sum();
    }

    /**
     * Gets the number of files in this operation.
     *
     * @return number of files
     */
    public int getFileCount() {
        return files.size();
    }

    /**
     * Creates a string representation of this operation.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return String.format(
                "BatchOperation{id='%s', type=%s, files=%d, priority=%s, size=%dMB, dependencies=%d}",
                operationId, operationType, files.size(), priority,
                getTotalFileSize() / (1024 * 1024), dependencies.size()
        );
    }

    /**
     * Resource requirements for batch operations.
     */
    public static class ResourceRequirements {
        public final long memoryBytes;
        public final int cpuCores;
        public final int ioBandwidthMBps;
        public final long timeoutMs;

        /**
         * Creates resource requirements.
         *
         * @param memoryBytes memory required in bytes
         * @param cpuCores CPU cores required
         * @param ioBandwidthMBps I/O bandwidth required in MB/s
         * @param timeoutMs timeout in milliseconds
         */
        public ResourceRequirements(long memoryBytes, int cpuCores, int ioBandwidthMBps, long timeoutMs) {
            this.memoryBytes = memoryBytes;
            this.cpuCores = cpuCores;
            this.ioBandwidthMBps = ioBandwidthMBps;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String toString() {
            return String.format(
                    "ResourceRequirements{memory=%dMB, cpu=%d cores, io=%dMB/s, timeout=%dms}",
                    memoryBytes / (1024 * 1024), cpuCores, ioBandwidthMBps, timeoutMs
            );
        }
    }
}