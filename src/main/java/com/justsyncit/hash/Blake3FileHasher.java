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
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File hashing implementation using BLAKE3 service.
 * Follows Single Responsibility Principle by focusing only on file operations.
 */
public class Blake3FileHasher implements FileHasher {

    private static final Logger logger = LoggerFactory.getLogger(Blake3FileHasher.class);
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for streaming

    private final StreamHasher streamHasher;
    private final BufferHasher bufferHasher;

    /**
     * Creates a new Blake3FileHasher with the provided dependencies.
     *
     * @param streamHasher the stream hashing service
     * @param bufferHasher the buffer hashing service
     */
    public Blake3FileHasher(StreamHasher streamHasher, BufferHasher bufferHasher) {
        this.streamHasher = streamHasher;
        this.bufferHasher = bufferHasher;
    }

    @Override
    public String hashFile(Path filePath) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }
        
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        
        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Path is not a regular file: " + filePath);
        }

        logger.debug("Hashing file: {}", filePath);
        
        long fileSize = Files.size(filePath);
        logger.trace("File size: {} bytes", fileSize);

        // For small files, read all at once for better performance
        if (fileSize <= BUFFER_SIZE) {
            byte[] data = Files.readAllBytes(filePath);
            return bufferHasher.hashBuffer(data);
        }

        // For large files, use streaming approach
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return streamHasher.hashStream(inputStream);
        }
    }
}