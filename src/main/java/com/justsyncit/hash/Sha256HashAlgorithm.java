/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hash algorithm implementation.
 * This class implements the HashAlgorithm interface using Java's built-in SHA-256.
 * Note: This is currently used as a fallback until a true BLAKE3 implementation is available.
 */
public final class Sha256HashAlgorithm implements HashAlgorithm {

    /** MessageDigest instance for SHA-256. */
    private final MessageDigest digest;

    /**
     * Creates a new SHA-256 hash algorithm instance.
     * @throws RuntimeException if SHA-256 algorithm is not available
     */
    private Sha256HashAlgorithm() {
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Creates a new SHA-256 hash algorithm instance.
     * @return a new Sha256HashAlgorithm instance
     * @throws RuntimeException if SHA-256 algorithm is not available
     */
    public static Sha256HashAlgorithm create() {
        return new Sha256HashAlgorithm();
    }

    @Override
    public void update(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        digest.update(data);
    }

    @Override
    public void update(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset or length");
        }
        digest.update(data, offset, length);
    }

    @Override
    public byte[] digest() {
        return digest.digest();
    }

    @Override
    public void reset() {
        digest.reset();
    }

    @Override
    public String getAlgorithmName() {
        return "SHA-256";
    }

    @Override
    public int getHashLength() {
        return 32; // SHA-256 produces 256-bit hash = 32 bytes
    }
}