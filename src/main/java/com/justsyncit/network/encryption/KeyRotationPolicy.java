/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.network.encryption;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Policy for determining when encryption keys should be rotated.
 * 
 * <p>
 * Rotation can be triggered by:
 * <ul>
 * <li>Key age (time since creation)</li>
 * <li>Usage count (number of encryption operations)</li>
 * <li>Data volume (bytes encrypted with the key)</li>
 * </ul>
 */
public final class KeyRotationPolicy {

    private static final Duration DEFAULT_MAX_AGE = Duration.ofDays(90);
    private static final long DEFAULT_MAX_BYTES = 1L << 40; // 1 TB
    private static final long DEFAULT_MAX_OPERATIONS = 1_000_000_000L; // 1 billion

    private final Duration maxAge;
    private final long maxBytesEncrypted;
    private final long maxOperations;

    private volatile Instant keyCreationTime;
    private final AtomicLong bytesEncrypted;
    private final AtomicLong operationCount;

    /**
     * Creates a policy with default thresholds.
     */
    public KeyRotationPolicy() {
        this(DEFAULT_MAX_AGE, DEFAULT_MAX_BYTES, DEFAULT_MAX_OPERATIONS);
    }

    /**
     * Creates a policy with custom thresholds.
     *
     * @param maxAge            maximum key age before rotation
     * @param maxBytesEncrypted maximum bytes encrypted before rotation
     * @param maxOperations     maximum encryption operations before rotation
     */
    public KeyRotationPolicy(Duration maxAge, long maxBytesEncrypted, long maxOperations) {
        this.maxAge = maxAge;
        this.maxBytesEncrypted = maxBytesEncrypted;
        this.maxOperations = maxOperations;
        this.keyCreationTime = Instant.now();
        this.bytesEncrypted = new AtomicLong(0);
        this.operationCount = new AtomicLong(0);
    }

    /**
     * Records an encryption operation for tracking usage.
     *
     * @param bytesProcessed number of bytes encrypted
     */
    public void recordOperation(long bytesProcessed) {
        operationCount.incrementAndGet();
        bytesEncrypted.addAndGet(bytesProcessed);
    }

    /**
     * Checks if key rotation is recommended based on current policy.
     *
     * @return true if the key should be rotated
     */
    public boolean shouldRotate() {
        return isAgeExceeded() || isByteLimitExceeded() || isOperationLimitExceeded();
    }

    /**
     * Checks if the key age exceeds the maximum.
     *
     * @return true if key is too old
     */
    public boolean isAgeExceeded() {
        return Duration.between(keyCreationTime, Instant.now()).compareTo(maxAge) > 0;
    }

    /**
     * Checks if the bytes encrypted exceed the maximum.
     *
     * @return true if too many bytes encrypted
     */
    public boolean isByteLimitExceeded() {
        return bytesEncrypted.get() >= maxBytesEncrypted;
    }

    /**
     * Checks if the operation count exceeds the maximum.
     *
     * @return true if too many operations performed
     */
    public boolean isOperationLimitExceeded() {
        return operationCount.get() >= maxOperations;
    }

    /**
     * Resets the policy counters after key rotation.
     *
     * @param newKeyCreationTime the creation time of the new key
     */
    public void reset(Instant newKeyCreationTime) {
        this.keyCreationTime = newKeyCreationTime;
        this.bytesEncrypted.set(0);
        this.operationCount.set(0);
    }

    /**
     * Resets the policy counters with current time as creation time.
     */
    public void reset() {
        reset(Instant.now());
    }

    /**
     * Gets the configured maximum key age.
     *
     * @return maximum age
     */
    public Duration getMaxAge() {
        return maxAge;
    }

    /**
     * Gets the configured maximum bytes limit.
     *
     * @return maximum bytes
     */
    public long getMaxBytesEncrypted() {
        return maxBytesEncrypted;
    }

    /**
     * Gets the configured maximum operations limit.
     *
     * @return maximum operations
     */
    public long getMaxOperations() {
        return maxOperations;
    }

    /**
     * Gets the current bytes encrypted count.
     *
     * @return bytes encrypted
     */
    public long getCurrentBytesEncrypted() {
        return bytesEncrypted.get();
    }

    /**
     * Gets the current operation count.
     *
     * @return operation count
     */
    public long getCurrentOperationCount() {
        return operationCount.get();
    }

    /**
     * Gets the key creation time.
     *
     * @return creation time
     */
    public Instant getKeyCreationTime() {
        return keyCreationTime;
    }
}
