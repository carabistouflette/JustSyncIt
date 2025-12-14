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

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Optional;

/**
 * Interface for secure key storage and management.
 * Provides operations for storing, retrieving, and rotating encryption keys.
 */
public interface SecureKeyStore {

    /**
     * Stores a secret key with the given alias.
     *
     * @param alias unique identifier for the key
     * @param key   the secret key to store
     * @throws EncryptionException if storage fails
     */
    void storeKey(String alias, SecretKey key) throws EncryptionException;

    /**
     * Retrieves a secret key by alias.
     *
     * @param alias the key identifier
     * @return the secret key, or empty if not found
     * @throws EncryptionException if retrieval fails
     */
    Optional<SecretKey> getKey(String alias) throws EncryptionException;

    /**
     * Deletes a key by alias.
     *
     * @param alias the key identifier
     * @return true if the key was deleted, false if not found
     * @throws EncryptionException if deletion fails
     */
    boolean deleteKey(String alias) throws EncryptionException;

    /**
     * Lists all key aliases in the store.
     *
     * @return list of key aliases
     * @throws EncryptionException if listing fails
     */
    List<String> listKeys() throws EncryptionException;

    /**
     * Checks if a key exists.
     *
     * @param alias the key identifier
     * @return true if the key exists
     * @throws EncryptionException if the check fails
     */
    boolean containsKey(String alias) throws EncryptionException;

    /**
     * Rotates a key by creating a new version while preserving the old one.
     *
     * @param baseAlias the base alias for the key
     * @param newKey    the new key to store
     * @return the alias of the new key (e.g., "baseAlias_v2")
     * @throws EncryptionException if rotation fails
     */
    String rotateKey(String baseAlias, SecretKey newKey) throws EncryptionException;

    /**
     * Gets the current (latest) key for a base alias.
     *
     * @param baseAlias the base alias
     * @return the current key, or empty if no keys exist
     * @throws EncryptionException if retrieval fails
     */
    Optional<SecretKey> getCurrentKey(String baseAlias) throws EncryptionException;

    /**
     * Generates a new random AES-256 key.
     *
     * @return a new secret key
     * @throws EncryptionException if generation fails
     */
    SecretKey generateKey() throws EncryptionException;
}
