package com.justsyncit.storage;

/**
 * Interface for content-addressable storage system.
 * Provides deduplication by storing chunks identified by their cryptographic
 * hash.
 * Follows Interface Segregation Principle by composing multiple focused
 * interfaces.
 */
public interface ContentStore extends
        ChunkStorage,
        StorageStatistics,
        GarbageCollectible,
        ClosableResource {
    // This interface now combines multiple focused interfaces
    // No additional methods needed - all functionality is inherited
}