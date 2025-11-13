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

package com.justsyncit;

import com.justsyncit.command.CommandRegistry;
import com.justsyncit.hash.Blake3BufferHasher;
import com.justsyncit.hash.Blake3FileHasher;
import com.justsyncit.hash.Blake3IncrementalHasherFactory;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.hash.Blake3ServiceImpl;
import com.justsyncit.hash.Blake3StreamHasher;
import com.justsyncit.hash.BufferHasher;
import com.justsyncit.hash.FileHasher;
import com.justsyncit.hash.HashAlgorithm;
import com.justsyncit.hash.IncrementalHasherFactory;
import com.justsyncit.hash.Sha256HashAlgorithm;
import com.justsyncit.hash.StreamHasher;
import com.justsyncit.simd.SimdDetectionService;
import com.justsyncit.simd.SimdDetectionServiceImpl;

/**
 * Factory for creating test services with mocked or minimal dependencies.
 * This helps simplify test setup while maintaining SOLID principles.
 */
public class TestServiceFactory {

    /**
     * Creates a Blake3Service with minimal dependencies for testing.
     *
     * @return Blake3Service instance for testing
     */
    public static Blake3Service createBlake3Service() {
        HashAlgorithm hashAlgorithm = Sha256HashAlgorithm.create();
        BufferHasher bufferHasher = new Blake3BufferHasher(hashAlgorithm);
        IncrementalHasherFactory incrementalHasherFactory = new Blake3IncrementalHasherFactory(hashAlgorithm);
        StreamHasher streamHasher = new Blake3StreamHasher(incrementalHasherFactory);
        FileHasher fileHasher = new Blake3FileHasher(streamHasher, bufferHasher);
        SimdDetectionService simdDetectionService = new SimdDetectionServiceImpl();

        return new Blake3ServiceImpl(
                fileHasher, bufferHasher, streamHasher,
                incrementalHasherFactory, simdDetectionService);
    }

    /**
     * Creates a JustSyncItApplication with minimal dependencies for testing.
     *
     * @return JustSyncItApplication instance for testing
     */
    public static JustSyncItApplication createApplication() {
        Blake3Service blake3Service = createBlake3Service();
        CommandRegistry commandRegistry = new CommandRegistry();
        ApplicationInfoDisplay infoDisplay = new ConsoleInfoDisplay();

        return new JustSyncItApplication(blake3Service, commandRegistry, infoDisplay);
    }

    /**
     * Creates a Blake3Service with mocked dependencies for testing.
     * This allows for more focused unit testing.
     *
     * @return Blake3Service instance with mocked dependencies
     */
    public static Blake3Service createBlake3ServiceWithMocks() {
        // In a real scenario, you would use a mocking framework like Mockito
        // For now, we'll use null dependencies and handle them gracefully in tests
        return new Blake3ServiceImpl(null, null, null, null, null);
    }
}