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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-system based secure key store using Java PKCS12 KeyStore.
 * 
 * <p>
 * Keys are stored in a password-protected PKCS12 file. The KeyStore password
 * should be derived from a master password using Argon2id.
 * 
 * <p>
 * Key versioning: When rotating keys, new versions are stored with a suffix
 * (e.g., "mykey_v1", "mykey_v2"). The current key is the highest version.
 */
public final class FileSystemSecureKeyStore implements SecureKeyStore {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemSecureKeyStore.class);
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String KEY_ALGORITHM = "AES";
    private static final int KEY_SIZE_BITS = 256;
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(.+)_v(\\d+)$");

    private final Path keystorePath;
    private final char[] keystorePassword;
    private final KeyStore keyStore;
    private final Object lock = new Object();

    /**
     * Opens or creates a key store at the specified path.
     *
     * @param keystorePath     path to the keystore file
     * @param keystorePassword password for the keystore
     * @throws EncryptionException if the keystore cannot be opened or created
     */
    public FileSystemSecureKeyStore(Path keystorePath, char[] keystorePassword)
            throws EncryptionException {
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword.clone();

        try {
            this.keyStore = KeyStore.getInstance(KEYSTORE_TYPE);

            if (Files.exists(keystorePath)) {
                // Load existing keystore
                try (InputStream is = Files.newInputStream(keystorePath)) {
                    keyStore.load(is, this.keystorePassword);
                }
                logger.debug("Loaded existing keystore from {}", keystorePath);
            } else {
                // Create new keystore
                keyStore.load(null, this.keystorePassword);
                // Ensure parent directories exist
                Path parent = keystorePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                save();
                logger.info("Created new keystore at {}", keystorePath);
            }
        } catch (Exception e) {
            throw new EncryptionException("Failed to initialize keystore", e);
        }
    }

    @Override
    public void storeKey(String alias, SecretKey key) throws EncryptionException {
        if (alias == null || alias.isEmpty()) {
            throw new EncryptionException("Alias cannot be null or empty");
        }
        if (key == null) {
            throw new EncryptionException("Key cannot be null");
        }

        synchronized (lock) {
            try {
                ProtectionParameter protParam = new KeyStore.PasswordProtection(keystorePassword);
                SecretKeyEntry entry = new SecretKeyEntry(key);
                keyStore.setEntry(alias, entry, protParam);
                save();
                logger.debug("Stored key with alias: {}", alias);
            } catch (KeyStoreException e) {
                throw new EncryptionException("Failed to store key: " + alias, e);
            }
        }
    }

    @Override
    public Optional<SecretKey> getKey(String alias) throws EncryptionException {
        if (alias == null || alias.isEmpty()) {
            return Optional.empty();
        }

        synchronized (lock) {
            try {
                if (!keyStore.containsAlias(alias)) {
                    return Optional.empty();
                }

                ProtectionParameter protParam = new KeyStore.PasswordProtection(keystorePassword);
                KeyStore.Entry entry = keyStore.getEntry(alias, protParam);

                if (entry instanceof SecretKeyEntry secretEntry) {
                    return Optional.of(secretEntry.getSecretKey());
                }
                return Optional.empty();

            } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
                throw new EncryptionException("Failed to retrieve key: " + alias, e);
            }
        }
    }

    @Override
    public boolean deleteKey(String alias) throws EncryptionException {
        if (alias == null || alias.isEmpty()) {
            return false;
        }

        synchronized (lock) {
            try {
                if (!keyStore.containsAlias(alias)) {
                    return false;
                }

                keyStore.deleteEntry(alias);
                save();
                logger.debug("Deleted key with alias: {}", alias);
                return true;

            } catch (KeyStoreException e) {
                throw new EncryptionException("Failed to delete key: " + alias, e);
            }
        }
    }

    @Override
    public List<String> listKeys() throws EncryptionException {
        synchronized (lock) {
            try {
                List<String> aliases = new ArrayList<>();
                Enumeration<String> aliasEnum = keyStore.aliases();
                while (aliasEnum.hasMoreElements()) {
                    aliases.add(aliasEnum.nextElement());
                }
                return Collections.unmodifiableList(aliases);

            } catch (KeyStoreException e) {
                throw new EncryptionException("Failed to list keys", e);
            }
        }
    }

    @Override
    public boolean containsKey(String alias) throws EncryptionException {
        if (alias == null || alias.isEmpty()) {
            return false;
        }

        synchronized (lock) {
            try {
                return keyStore.containsAlias(alias);
            } catch (KeyStoreException e) {
                throw new EncryptionException("Failed to check key existence: " + alias, e);
            }
        }
    }

    @Override
    public String rotateKey(String baseAlias, SecretKey newKey) throws EncryptionException {
        if (baseAlias == null || baseAlias.isEmpty()) {
            throw new EncryptionException("Base alias cannot be null or empty");
        }

        synchronized (lock) {
            // Find current highest version
            int highestVersion = 0;

            try {
                Enumeration<String> aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    int version = extractVersion(alias, baseAlias);
                    highestVersion = Math.max(highestVersion, version);
                }
            } catch (KeyStoreException e) {
                throw new EncryptionException("Failed to enumerate keys for rotation", e);
            }

            // Create new versioned alias
            int newVersion = highestVersion + 1;
            String newAlias = baseAlias + "_v" + newVersion;

            storeKey(newAlias, newKey);
            logger.info("Rotated key {} to version {}", baseAlias, newVersion);

            return newAlias;
        }
    }

    @Override
    public Optional<SecretKey> getCurrentKey(String baseAlias) throws EncryptionException {
        if (baseAlias == null || baseAlias.isEmpty()) {
            return Optional.empty();
        }

        synchronized (lock) {
            String highestAlias = null;
            int highestVersion = -1;

            try {
                Enumeration<String> aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    int version = extractVersion(alias, baseAlias);
                    if (version > highestVersion) {
                        highestVersion = version;
                        highestAlias = alias;
                    }
                }
            } catch (KeyStoreException e) {
                throw new EncryptionException("Failed to find current key for: " + baseAlias, e);
            }

            if (highestAlias != null) {
                return getKey(highestAlias);
            }
            return Optional.empty();
        }
    }

    @Override
    public SecretKey generateKey() throws EncryptionException {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGen.init(KEY_SIZE_BITS);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new EncryptionException("Failed to generate key", e);
        }
    }

    /**
     * Saves the keystore to disk with atomic write.
     */
    private void save() throws EncryptionException {
        Path tempFile = null;
        try {
            // Get parent directory, fall back to current directory if null
            Path parentDir = keystorePath.getParent();
            if (parentDir == null) {
                parentDir = keystorePath.toAbsolutePath().getParent();
            }

            // Write to temp file first for atomic save
            tempFile = Files.createTempFile(
                    parentDir,
                    "keystore_",
                    ".tmp");

            try (OutputStream os = Files.newOutputStream(tempFile)) {
                keyStore.store(os, keystorePassword);
            }

            // Atomic move
            Files.move(tempFile, keystorePath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (Exception e) {
            // Clean up temp file on failure
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best effort cleanup
                }
            }
            throw new EncryptionException("Failed to save keystore", e);
        }
    }

    /**
     * Extracts version number from alias if it matches the base alias pattern.
     * 
     * <p>
     * Note: PKCS12 keystores lowercase all aliases, so we use case-insensitive
     * comparison.
     *
     * @return version number, or 0 if exact match, or -1 if no match
     */
    private int extractVersion(String alias, String baseAlias) {
        if (alias.equalsIgnoreCase(baseAlias)) {
            return 0; // Unversioned alias counts as version 0
        }

        Matcher matcher = VERSION_PATTERN.matcher(alias);
        if (matcher.matches() && matcher.group(1).equalsIgnoreCase(baseAlias)) {
            return Integer.parseInt(matcher.group(2));
        }

        return -1; // Not a match
    }
}
