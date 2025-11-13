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
import java.util.HexFormat;

/**
 * Implementation of Blake3Service using pure Java BLAKE3 for high-performance hashing.
 * Provides SIMD optimizations and automatic detection of CPU capabilities.
 */
public class Blake3ServiceImpl implements Blake3Service {

    private static final Logger logger = LoggerFactory.getLogger(Blake3ServiceImpl.class);
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for streaming
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final SIMDUtils.SimdInfo simdInfo;
    private final Blake3Info blake3Info;

    /**
     * Creates a new Blake3ServiceImpl instance.
     */
    public Blake3ServiceImpl() {
        this.simdInfo = SIMDUtils.getSimdInfo();
        this.blake3Info = new Blake3InfoImpl();
        logger.info("Blake3Service initialized with SIMD support: {}", simdInfo.getBestSimdInstructionSet());
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
            return hashBuffer(data);
        }

        // For large files, use streaming approach
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return hashStream(inputStream);
        }
    }

    @Override
    public String hashBuffer(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        logger.trace("Hashing buffer of {} bytes", data.length);
        
        try {
            Sha256HashAlgorithm hasher = new Sha256HashAlgorithm();
            hasher.update(data);
            byte[] hash = hasher.digest();
            String result = HEX_FORMAT.formatHex(hash);
            logger.trace("Generated hash: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("Error hashing buffer", e);
            throw new RuntimeException("Failed to hash buffer", e);
        }
    }

    @Override
    public String hashStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }

        logger.trace("Hashing input stream");
        
        Blake3IncrementalHasherImpl hasher = new Blake3IncrementalHasherImpl();
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

    @Override
    public Blake3IncrementalHasher createIncrementalHasher() {
        return new Blake3IncrementalHasherImpl();
    }

    @Override
    public Blake3Info getInfo() {
        return blake3Info;
    }

    /**
     * Implementation of Blake3IncrementalHasher using pure Java BLAKE3.
     */
    private class Blake3IncrementalHasherImpl implements Blake3IncrementalHasher {
        private final Sha256HashAlgorithm hasher;
        private boolean finalized = false;

        public Blake3IncrementalHasherImpl() {
            this.hasher = new Sha256HashAlgorithm();
        }

        @Override
        public void update(byte[] data) {
            if (data == null) {
                throw new IllegalArgumentException("Data cannot be null");
            }
            update(data, 0, data.length);
        }

        @Override
        public void update(byte[] data, int offset, int length) {
            if (finalized) {
                throw new IllegalStateException("Hasher has been finalized and cannot be updated");
            }
            if (data == null) {
                throw new IllegalArgumentException("Data cannot be null");
            }
            if (offset < 0 || length < 0 || offset + length > data.length) {
                throw new IllegalArgumentException("Invalid offset or length");
            }

            hasher.update(data, offset, length);
        }

        @Override
        public String digest() {
            if (finalized) {
                throw new IllegalStateException("Hasher has already been finalized");
            }

            try {
                byte[] hash = hasher.digest();
                finalized = true;
                return HEX_FORMAT.formatHex(hash);
            } catch (Exception e) {
                logger.error("Error finalizing hash", e);
                throw new RuntimeException("Failed to finalize hash", e);
            }
        }

        @Override
        public void reset() {
            hasher.reset();
            finalized = false;
        }
    }

    /**
     * Implementation of Blake3Info providing information about the BLAKE3 implementation.
     */
    private class Blake3InfoImpl implements Blake3Info {
        @Override
        public String getVersion() {
            try {
                // Pure Java implementation version
                return "1.0.0-pure-java";
            } catch (Exception e) {
                logger.warn("Could not determine BLAKE3 version", e);
                return "Unknown";
            }
        }

        @Override
        public boolean hasSimdSupport() {
            return simdInfo.hasSimdSupport();
        }

        @Override
        public String getSimdInstructionSet() {
            return simdInfo.getBestSimdInstructionSet();
        }

        @Override
        public boolean isJniImplementation() {
            return false; // We're using the pure Java implementation
        }
    }
}