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

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Argon2id key derivation service using Bouncy Castle.
 * 
 * <p>
 * Argon2id is recommended for password hashing and key derivation as it
 * provides:
 * <ul>
 * <li>Memory-hard computation (resistant to GPU/ASIC attacks)</li>
 * <li>Protection against both side-channel and time-memory trade-off
 * attacks</li>
 * </ul>
 * 
 * <p>
 * Default parameters:
 * <ul>
 * <li>Memory: 512 KB (can be increased for higher security)</li>
 * <li>Iterations: 3</li>
 * <li>Parallelism: 4 lanes</li>
 * <li>Salt: 16 bytes</li>
 * </ul>
 */
public final class Argon2idKeyDerivationService implements KeyDerivationService {

    private static final int DEFAULT_MEMORY_KB = 512; // 512 KB memory cost
    private static final int DEFAULT_ITERATIONS = 3; // 3 iterations
    private static final int DEFAULT_PARALLELISM = 4; // 4 parallel lanes
    private static final int SALT_SIZE_BYTES = 16; // 128-bit salt

    private final int memoryKb;
    private final int iterations;
    private final int parallelism;
    private final SecureRandom secureRandom;

    /**
     * Constructs an Argon2id service with default parameters.
     */
    public Argon2idKeyDerivationService() {
        this(DEFAULT_MEMORY_KB, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM);
    }

    /**
     * Constructs an Argon2id service with custom parameters.
     *
     * @param memoryKb    memory cost in KB
     * @param iterations  number of iterations
     * @param parallelism degree of parallelism
     */
    public Argon2idKeyDerivationService(int memoryKb, int iterations, int parallelism) {
        this.memoryKb = memoryKb;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public byte[] deriveKey(char[] password, byte[] salt, int outputLength)
            throws EncryptionException {
        if (password == null || password.length == 0) {
            throw new EncryptionException("Password cannot be null or empty");
        }
        if (salt == null || salt.length < 8) {
            throw new EncryptionException("Salt must be at least 8 bytes");
        }
        if (outputLength < 1) {
            throw new EncryptionException("Output length must be positive");
        }

        // Convert char[] to byte[] (UTF-8 encoding)
        byte[] passwordBytes = toBytes(password);

        try {
            Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(salt)
                    .withMemoryAsKB(memoryKb)
                    .withIterations(iterations)
                    .withParallelism(parallelism)
                    .build();

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(params);

            byte[] output = new byte[outputLength];
            generator.generateBytes(passwordBytes, output);

            return output;

        } catch (Exception e) {
            throw new EncryptionException("Key derivation failed", e);
        } finally {
            // Clear sensitive data from memory
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    @Override
    public byte[] generateSalt() {
        byte[] salt = new byte[SALT_SIZE_BYTES];
        secureRandom.nextBytes(salt);
        return salt;
    }

    @Override
    public int getSaltSize() {
        return SALT_SIZE_BYTES;
    }

    @Override
    public String getAlgorithmName() {
        return "Argon2id";
    }

    /**
     * Gets the configured memory cost in KB.
     *
     * @return memory cost
     */
    public int getMemoryKb() {
        return memoryKb;
    }

    /**
     * Gets the configured iteration count.
     *
     * @return iterations
     */
    public int getIterations() {
        return iterations;
    }

    /**
     * Gets the configured parallelism.
     *
     * @return parallelism
     */
    public int getParallelism() {
        return parallelism;
    }

    /**
     * Converts char array to byte array using UTF-8 encoding.
     */
    private byte[] toBytes(char[] chars) {
        StringBuilder sb = new StringBuilder(chars.length);
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
