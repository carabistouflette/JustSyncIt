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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for FileSystemSecureKeyStore.
 */
class FileSystemSecureKeyStoreTest {

    @TempDir
    Path tempDir;

    private FileSystemSecureKeyStore keyStore;
    private Path keystorePath;
    private char[] password;

    @BeforeEach
    void setUp() throws EncryptionException {
        keystorePath = tempDir.resolve("test.p12");
        password = "testPassword123".toCharArray();
        keyStore = new FileSystemSecureKeyStore(keystorePath, password);
    }

    @Test
    @DisplayName("Store and retrieve a key")
    void testStoreAndRetrieveKey() throws EncryptionException {
        SecretKey key = keyStore.generateKey();
        String alias = "testKey";

        keyStore.storeKey(alias, key);
        Optional<SecretKey> retrieved = keyStore.getKey(alias);

        assertTrue(retrieved.isPresent());
        assertArrayEquals(key.getEncoded(), retrieved.get().getEncoded());
    }

    @Test
    @DisplayName("Delete a key")
    void testDeleteKey() throws EncryptionException {
        SecretKey key = keyStore.generateKey();
        String alias = "keyToDelete";

        keyStore.storeKey(alias, key);
        assertTrue(keyStore.containsKey(alias));

        boolean deleted = keyStore.deleteKey(alias);
        assertTrue(deleted);
        assertFalse(keyStore.containsKey(alias));
    }

    @Test
    @DisplayName("Delete non-existent key returns false")
    void testDeleteNonExistentKey() throws EncryptionException {
        boolean deleted = keyStore.deleteKey("nonExistentKey");
        assertFalse(deleted);
    }

    @Test
    @DisplayName("List all keys")
    void testListKeys() throws EncryptionException {
        keyStore.storeKey("key1", keyStore.generateKey());
        keyStore.storeKey("key2", keyStore.generateKey());
        keyStore.storeKey("key3", keyStore.generateKey());

        List<String> keys = keyStore.listKeys();

        assertEquals(3, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
        assertTrue(keys.contains("key3"));
    }

    @Test
    @DisplayName("Get non-existent key returns empty")
    void testGetNonExistentKey() throws EncryptionException {
        Optional<SecretKey> key = keyStore.getKey("nonExistent");
        assertTrue(key.isEmpty());
    }

    @Test
    @DisplayName("Contains key returns correct state")
    void testContainsKey() throws EncryptionException {
        assertFalse(keyStore.containsKey("myKey"));

        keyStore.storeKey("myKey", keyStore.generateKey());
        assertTrue(keyStore.containsKey("myKey"));
    }

    @Test
    @DisplayName("Key rotation creates versioned keys")
    void testKeyRotation() throws EncryptionException {
        SecretKey key1 = keyStore.generateKey();
        SecretKey key2 = keyStore.generateKey();
        SecretKey key3 = keyStore.generateKey();

        String alias1 = keyStore.rotateKey("myKey", key1);
        String alias2 = keyStore.rotateKey("myKey", key2);
        String alias3 = keyStore.rotateKey("myKey", key3);

        // Note: PKCS12 lowercases aliases, but internally we use the input case
        // The returned aliases use the casing we provide
        assertEquals("myKey_v1", alias1);
        assertEquals("myKey_v2", alias2);
        assertEquals("myKey_v3", alias3);

        // PKCS12 stores as lowercase, so check with lowercase
        assertTrue(keyStore.containsKey("mykey_v1"));
        assertTrue(keyStore.containsKey("mykey_v2"));
        assertTrue(keyStore.containsKey("mykey_v3"));
    }

    @Test
    @DisplayName("Get current key returns latest version")
    void testGetCurrentKey() throws EncryptionException {
        SecretKey key1 = keyStore.generateKey();
        SecretKey key2 = keyStore.generateKey();
        SecretKey key3 = keyStore.generateKey();

        keyStore.rotateKey("myKey", key1);
        keyStore.rotateKey("myKey", key2);
        keyStore.rotateKey("myKey", key3);

        Optional<SecretKey> currentKey = keyStore.getCurrentKey("myKey");

        assertTrue(currentKey.isPresent());
        assertArrayEquals(key3.getEncoded(), currentKey.get().getEncoded());
    }

    @Test
    @DisplayName("Get current key for non-existent base alias returns empty")
    void testGetCurrentKeyNonExistent() throws EncryptionException {
        Optional<SecretKey> key = keyStore.getCurrentKey("nonExistent");
        assertTrue(key.isEmpty());
    }

    @Test
    @DisplayName("Generate key creates valid AES-256 key")
    void testGenerateKey() throws EncryptionException {
        SecretKey key = keyStore.generateKey();

        assertNotNull(key);
        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length); // 256 bits
    }

    @Test
    @DisplayName("Keystore persists to disk")
    void testKeystorePersistence() throws EncryptionException {
        SecretKey key = keyStore.generateKey();
        keyStore.storeKey("persistedKey", key);

        // Create new keystore instance from same file
        FileSystemSecureKeyStore reloaded = new FileSystemSecureKeyStore(keystorePath, password);

        Optional<SecretKey> retrieved = reloaded.getKey("persistedKey");
        assertTrue(retrieved.isPresent());
        assertArrayEquals(key.getEncoded(), retrieved.get().getEncoded());
    }

    @Test
    @DisplayName("Null alias should throw for store")
    void testNullAliasThrowsOnStore() {
        SecretKey key = assertDoesNotThrow(() -> keyStore.generateKey());

        assertThrows(EncryptionException.class,
                () -> keyStore.storeKey(null, key));
    }

    @Test
    @DisplayName("Empty alias should throw for store")
    void testEmptyAliasThrowsOnStore() throws EncryptionException {
        SecretKey key = keyStore.generateKey();

        assertThrows(EncryptionException.class,
                () -> keyStore.storeKey("", key));
    }

    @Test
    @DisplayName("Null key should throw for store")
    void testNullKeyThrowsOnStore() {
        assertThrows(EncryptionException.class,
                () -> keyStore.storeKey("alias", null));
    }

    @Test
    @DisplayName("Creates parent directories if not exist")
    void testCreatesParentDirectories() throws EncryptionException {
        Path nestedPath = tempDir.resolve("nested/dir/keystore.p12");

        FileSystemSecureKeyStore nestedStore = new FileSystemSecureKeyStore(nestedPath, password);

        nestedStore.storeKey("test", nestedStore.generateKey());
        assertTrue(Files.exists(nestedPath));
    }

    private static <T> T assertDoesNotThrow(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception, but got: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
