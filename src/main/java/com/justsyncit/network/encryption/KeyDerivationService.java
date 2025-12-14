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

/**
 * Interface for key derivation services.
 * Derives cryptographic keys from passwords using secure key derivation
 * functions.
 */
public interface KeyDerivationService {

    /**
     * Derives a cryptographic key from a password and salt.
     *
     * @param password     the password to derive the key from
     * @param salt         the salt (should be random, at least 16 bytes)
     * @param outputLength desired key length in bytes
     * @return the derived key
     * @throws EncryptionException if key derivation fails
     */
    byte[] deriveKey(char[] password, byte[] salt, int outputLength) throws EncryptionException;

    /**
     * Derives a cryptographic key using default output length (32 bytes / 256
     * bits).
     *
     * @param password the password to derive the key from
     * @param salt     the salt
     * @return the derived 256-bit key
     * @throws EncryptionException if key derivation fails
     */
    default byte[] deriveKey(char[] password, byte[] salt) throws EncryptionException {
        return deriveKey(password, salt, 32);
    }

    /**
     * Generates a random salt.
     *
     * @return a random salt suitable for this KDF
     */
    byte[] generateSalt();

    /**
     * Gets the recommended salt size in bytes.
     *
     * @return salt size in bytes
     */
    int getSaltSize();

    /**
     * Gets the algorithm name.
     *
     * @return algorithm identifier
     */
    String getAlgorithmName();
}
