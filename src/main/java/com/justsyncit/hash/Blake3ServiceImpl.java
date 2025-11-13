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

import com.justsyncit.simd.SimdDetectionService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Refactored BLAKE3 service that follows SOLID principles.
 * This class acts as a facade that composes smaller, focused services.
 */
public class Blake3ServiceImpl implements Blake3Service {

    private final FileHasher fileHasher;
    private final BufferHasher bufferHasher;
    private final StreamHasher streamHasher;
    private final IncrementalHasherFactory incrementalHasherFactory;
    private final Blake3Info blake3Info;

    /**
     * Creates a new Blake3ServiceRefactored with all required dependencies.
     *
     * @param fileHasher the file hashing service
     * @param bufferHasher the buffer hashing service
     * @param streamHasher the stream hashing service
     * @param incrementalHasherFactory the incremental hasher factory
     * @param simdDetectionService the SIMD detection service
     */
    public Blake3ServiceImpl(
            FileHasher fileHasher,
            BufferHasher bufferHasher,
            StreamHasher streamHasher,
            IncrementalHasherFactory incrementalHasherFactory,
            SimdDetectionService simdDetectionService) {
        this.fileHasher = fileHasher;
        this.bufferHasher = bufferHasher;
        this.streamHasher = streamHasher;
        this.incrementalHasherFactory = incrementalHasherFactory;
        this.blake3Info = new Blake3InfoImpl(simdDetectionService);
    }

    @Override
    public String hashFile(Path filePath) throws IOException {
        return fileHasher.hashFile(filePath);
    }

    @Override
    public String hashBuffer(byte[] data) {
        return bufferHasher.hashBuffer(data);
    }

    @Override
    public String hashStream(InputStream inputStream) throws IOException {
        return streamHasher.hashStream(inputStream);
    }

    @Override
    public Blake3IncrementalHasher createIncrementalHasher() {
        return new IncrementalHasherAdapter(incrementalHasherFactory.createIncrementalHasher());
    }

    @Override
    public Blake3Info getInfo() {
        return blake3Info;
    }

    /**
     * Implementation of Blake3Info providing information about the BLAKE3 implementation.
     */
    private static class Blake3InfoImpl implements Blake3Info {
        private final SimdDetectionService simdDetectionService;

        public Blake3InfoImpl(SimdDetectionService simdDetectionService) {
            this.simdDetectionService = simdDetectionService;
        }

        @Override
        public String getVersion() {
            try {
                // Pure Java implementation version
                return "1.0.0-pure-java";
            } catch (Exception e) {
                return "Unknown";
            }
        }

        @Override
        public boolean hasSimdSupport() {
            return simdDetectionService.getSimdInfo().hasSimdSupport();
        }

        @Override
        public String getSimdInstructionSet() {
            return simdDetectionService.getSimdInfo().getBestSimdInstructionSet();
        }

        @Override
        public boolean isJniImplementation() {
            return false; // We're using the pure Java implementation
        }
    }
}