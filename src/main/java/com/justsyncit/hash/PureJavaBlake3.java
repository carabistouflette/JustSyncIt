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
 * Pure Java implementation of BLAKE3 using SHA-256 as fallback.
 * This provides compatibility while we work on a full BLAKE3 implementation.
 * Note: This is a temporary implementation using SHA-256 for compatibility.
 */
public class PureJavaBlake3 {

    private final MessageDigest digest;

    public PureJavaBlake3() {
        try {
            // Using SHA-256 as fallback for now
            // In a production environment, this should be replaced with actual BLAKE3
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public void update(byte[] data) {
        digest.update(data);
    }

    public void update(byte[] data, int offset, int length) {
        digest.update(data, offset, length);
    }

    public byte[] digest() {
        return digest.digest();
    }

    public void reset() {
        digest.reset();
    }
}