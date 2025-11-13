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
import com.justsyncit.command.HashCommand;
import com.justsyncit.command.VerifyCommand;
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
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.FilesystemChunkIndex;
import com.justsyncit.storage.FilesystemContentStore;

import java.io.IOException;

/**
 * Factory for creating application services and dependencies.
 * Follows Dependency Inversion Principle by providing abstractions.
 */
public class ServiceFactory {

    /**
     * Creates a fully configured JustSyncItApplicationRefactored.
     *
     * @return configured application instance
     */
    public JustSyncItApplication createApplication() {
        Blake3Service blake3Service = createBlake3Service();
        CommandRegistry commandRegistry = createCommandRegistry(blake3Service);
        ApplicationInfoDisplay infoDisplay = createInfoDisplay();

        return new JustSyncItApplication(blake3Service, commandRegistry, infoDisplay);
    }

    /**
     * Creates a BLAKE3 service with all dependencies.
     *
     * @return configured BLAKE3 service
     */
    private Blake3Service createBlake3Service() {
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
     * Creates a content store with all dependencies.
     *
     * @param blake3Service BLAKE3 service for hashing
     * @return configured content store
     */
    ContentStore createContentStore(Blake3Service blake3Service) throws IOException {
        java.nio.file.Path storageDir = java.nio.file.Paths.get("storage", "chunks");
        java.nio.file.Path indexFile = java.nio.file.Paths.get("storage", "index.txt");

        FilesystemChunkIndex chunkIndex = FilesystemChunkIndex.create(storageDir, indexFile);
        return FilesystemContentStore.create(storageDir, chunkIndex, blake3Service);
    }

    /**
     * Creates a command registry with all commands.
     *
     * @param blake3Service BLAKE3 service for commands
     * @return configured command registry
     */
    private CommandRegistry createCommandRegistry(Blake3Service blake3Service) {
        CommandRegistry registry = new CommandRegistry();

        // Register commands
        registry.register(new HashCommand(blake3Service));
        registry.register(new VerifyCommand(blake3Service));

        return registry;
    }

    /**
     * Creates an application info display.
     *
     * @return info display instance
     */
    private ApplicationInfoDisplay createInfoDisplay() {
        return new ConsoleInfoDisplay();
    }
}