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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stream hashing implementation using BLAKE3 algorithm.
 * Follows Single Responsibility Principle by focusing only on stream operations.
 */
public class Blake3StreamHasher implements StreamHasher {

    private static final Logger logger = LoggerFactory.getLogger(Blake3StreamHasher.class);
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for streaming

    private final IncrementalHasherFactory incrementalHasherFactory;

    /**
     * Creates a new Blake3StreamHasher with the provided factory.
     *
     * @param incrementalHasherFactory the factory for creating incremental hashers
     */
    public Blake3StreamHasher(IncrementalHasherFactory incrementalHasherFactory) {
        this.incrementalHasherFactory = incrementalHasherFactory;
    }

    @Override
    public String hashStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }

        logger.trace("Hashing input stream");
        
        IncrementalHasherFactory.IncrementalHasher hasher = incrementalHasherFactory.createIncrementalHasher();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                hasher.update(buffer, 0, bytesRead);
            }
            
            return hasher.digest();
        } catch (IOException e) {
            logger.error("Error reading from input stream", e);
            throw e;
        } catch (Exception e) {
            logger.error("Error hashing stream", e);
            throw new RuntimeException("Failed to hash stream", e);
        }
    }
}