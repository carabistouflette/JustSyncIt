package com.justsyncit.storage;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.hash.HashingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of IntegrityVerifier using BLAKE3 hashing service.
 * Provides cryptographic integrity verification for stored chunks.
 */
public final class Blake3IntegrityVerifier implements IntegrityVerifier {

    /** Logger instance. */
    private static final Logger logger = LoggerFactory.getLogger(Blake3IntegrityVerifier.class);

    /** The BLAKE3 service for hashing. */
    private final Blake3Service blake3Service;

    /**
     * Creates a new Blake3IntegrityVerifier.
     *
     * @param blake3Service the BLAKE3 service for hashing
     * @throws IllegalArgumentException if blake3Service is null
     */
    public Blake3IntegrityVerifier(Blake3Service blake3Service) {
        if (blake3Service == null) {
            throw new IllegalArgumentException("BLAKE3 service cannot be null");
        }
        this.blake3Service = blake3Service;
    }

    @Override
    public void verifyIntegrity(byte[] data, String expectedHash) throws StorageIntegrityException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        validateHash(expectedHash);

        String actualHash;
        try {
            actualHash = calculateHash(data);
        } catch (HashingException e) {
            throw new StorageIntegrityException("Failed to calculate hash for integrity verification", e);
        }

        if (!expectedHash.equals(actualHash)) {
            String message = String.format("Integrity check failed. Expected: %s, Actual: %s",
                    expectedHash, actualHash);
            logger.error(message);
            throw new StorageIntegrityException(message);
        }

        logger.debug("Integrity verification passed for hash {}", expectedHash);
    }

    @Override
    public String calculateHash(byte[] data) throws HashingException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        return blake3Service.hashBuffer(data);
    }

    @Override
    public void validateHash(String hash) {
        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("Hash cannot be null or empty");
        }
    }
}