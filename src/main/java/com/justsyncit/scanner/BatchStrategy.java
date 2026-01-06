package com.justsyncit.scanner;

/**
 * Enumeration of batch processing strategies for optimizing performance
 * based on different file characteristics and system conditions.
 */
public enum BatchStrategy {

    /**
     * Groups files by size for optimal processing.
     * Small files are batched together, large files are processed individually.
     */
    SIZE_BASED("SizeBased", "Groups files by size for optimal processing"),

    /**
     * Groups files by storage location for I/O optimization.
     * Files on the same disk/device are processed together.
     */
    LOCATION_BASED("LocationBased", "Groups files by storage location for I/O optimization"),

    /**
     * Processes critical files first based on priority.
     * Important files are processed before less important ones.
     */
    PRIORITY_BASED("PriorityBased", "Processes critical files first based on priority"),

    /**
     * Adapts batch sizes based on system resources.
     * Batch sizes are dynamically adjusted based on CPU and memory availability.
     */
    RESOURCE_AWARE("ResourceAware", "Adapts batch sizes based on system resources"),

    /**
     * Balances throughput and latency for mixed workloads.
     * Optimizes for both large file throughput and small file latency.
     */
    BALANCED("Balanced", "Balances throughput and latency for mixed workloads"),

    /**
     * Optimizes for NVMe SSD performance characteristics.
     * Uses larger batches and parallel I/O for optimal NVMe throughput.
     */
    NVME_OPTIMIZED("NVMeOptimized", "Optimizes for NVMe SSD performance characteristics"),

    /**
     * Optimizes for traditional HDD performance characteristics.
     * Uses sequential processing to minimize seek times.
     */
    HDD_OPTIMIZED("HDDOptimized", "Optimizes for traditional HDD performance characteristics");

    private final String name;
    private final String description;

    /**
     * Creates a new BatchStrategy.
     *
     * @param name        the strategy name
     * @param description the strategy description
     */
    BatchStrategy(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Gets the strategy name.
     *
     * @return the strategy name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the strategy description.
     *
     * @return the strategy description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }
}