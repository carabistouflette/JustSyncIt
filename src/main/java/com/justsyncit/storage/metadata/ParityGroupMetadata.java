package com.justsyncit.storage.metadata;

import java.time.Instant;

/**
 * Represents a group of chunks protected by error correction parity.
 */
public final class ParityGroupMetadata {
    private final long id;
    private final String algorithm;
    private final Instant createdAt;

    public ParityGroupMetadata(long id, String algorithm, Instant createdAt) {
        this.id = id;
        this.algorithm = algorithm;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "ParityGroup{id=" + id + ", algo='" + algorithm + "'}";
    }
}
