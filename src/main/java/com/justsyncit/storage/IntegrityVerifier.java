package com.justsyncit.storage;

import com.justsyncit.hash.HashingException;

/**
 * Interface for verifying the integrity of stored chunks.
 * Different implementations can use different hashing algorithms or
 * verification strategies.
 */
public interface IntegrityVerifier {

    /**
     * Verifies the integrity of data against its expected hash.
     *
     * @param data         the data to verify
     * @param expectedHash the expected hash of the data
     * @throws StorageIntegrityException if the integrity verification fails
     * @throws IllegalArgumentException  if data or expectedHash is null
     */
    void verifyIntegrity(byte[] data, String expectedHash) throws StorageIntegrityException;

    /**
     * Calculates the hash of the given data.
     *
     * @param data the data to hash
     * @return the hash of the data
     * @throws IllegalArgumentException if data is null
     * @throws HashingException         if hashing fails
     */
    String calculateHash(byte[] data) throws HashingException;

    /**
     * Validates that a hash is compatible with this verifier.
     *
     * @param hash the hash to validate
     * @throws IllegalArgumentException if hash is null or invalid
     */
    void validateHash(String hash);
}