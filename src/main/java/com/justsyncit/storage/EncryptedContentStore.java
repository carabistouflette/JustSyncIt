/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.justsyncit.storage;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.encryption.EncryptionException;
import com.justsyncit.network.encryption.EncryptionService;

import java.io.IOException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator for ContentStore that encrypts data before storage and decrypts on
 * retrieval.
 * Uses deterministic encryption to preserve deduplication capabilities.
 */
public class EncryptedContentStore implements ContentStore {

    private static final Logger logger = LoggerFactory.getLogger(EncryptedContentStore.class);

    private final ContentStore delegate;
    private final EncryptionService encryptionService;
    private final Supplier<byte[]> keySupplier;
    private final Blake3Service blake3Service;

    /**
     * Creates a new encrypted content store.
     *
     * @param delegate          the underlying content store
     * @param encryptionService the encryption service
     * @param keySupplier       supplier for the encryption key
     * @param blake3Service     hashing service for deterministic IV generation
     */
    public EncryptedContentStore(ContentStore delegate,
            EncryptionService encryptionService,
            Supplier<byte[]> keySupplier,
            Blake3Service blake3Service) {
        this.delegate = delegate;
        this.encryptionService = encryptionService;
        this.keySupplier = keySupplier;
        this.blake3Service = blake3Service;
    }

    @Override
    public String storeChunk(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        try {
            // 1. Calculate hash of plaintext to use as IV seed for deterministic encryption
            // This ensures identical chunks produce identical ciphertext for deduplication
            String plaintextHash = blake3Service.hashBuffer(data);
            byte[] ivSeed = hexStringToByteArray(plaintextHash);

            // 2. Encrypt the chunk
            byte[] key = keySupplier.get();
            if (key == null) {
                throw new IOException("Encryption key not available");
            }

            // We use the plaintext hash as the IV seed.
            // Note: We don't use associated data here to support simple deduplication.
            // If we bound it to file ID, we would break deduplication across files.
            byte[] encryptedData = encryptionService.encryptDeterministic(data, key, ivSeed, null);

            // 3. Store the encrypted blob
            // The delegate will return the hash of the ENCRYPTED blob.
            // This is what we return to the caller. The "system" knows chunks by their
            // encrypted hash.
            String blobHash = delegate.storeChunk(encryptedData);

            logger.debug("Stored encrypted chunk. Plaintext hash: {}, Ciphertext hash: {}",
                    plaintextHash, blobHash);

            return blobHash;

        } catch (EncryptionException e) {
            throw new IOException("Failed to encrypt chunk", e);
        }
    }

    @Override
    public byte[] retrieveChunk(String hash) throws IOException, StorageIntegrityException {
        // 1. Retrieve the encrypted blob
        byte[] encryptedData = delegate.retrieveChunk(hash);

        if (encryptedData == null) {
            return null;
        }

        try {
            // 2. Decrypt the blob
            byte[] key = keySupplier.get();
            if (key == null) {
                throw new IOException("Encryption key not available");
            }

            // Decrypt extracts IV from the blob.
            return encryptionService.decrypt(encryptedData, key);

        } catch (EncryptionException e) {
            throw new StorageIntegrityException("Failed to decrypt chunk " + hash, e);
        }
    }

    @Override
    public boolean existsChunk(String hash) throws IOException {
        // We check if the ENCRYPTED blob exists.
        // The calling service (BackupService) holds hashes of encrypted blobs in its
        // metadata.
        // So when it asks "existsChunk(H)", H is the hash of the encrypted blob.
        return delegate.existsChunk(hash);
    }

    @Override
    public long getChunkCount() throws IOException {
        return delegate.getChunkCount();
    }

    @Override
    public long getTotalSize() throws IOException {
        return delegate.getTotalSize();
    }

    @Override
    public ContentStoreStats getStats() throws IOException {
        return delegate.getStats();
    }

    @Override
    public long garbageCollect(java.util.Set<String> activeHashes) throws IOException {
        // Garbage collection operates on encrypted blob hashes, which is what
        // activeHashes should contain
        // (since metadata service stores encrypted blob hashes for chunks).
        return delegate.garbageCollect(activeHashes);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    // Helper to convert hex string to byte array
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
