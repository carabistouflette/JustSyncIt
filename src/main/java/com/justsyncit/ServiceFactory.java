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
import com.justsyncit.network.command.NetworkCommand;
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
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.NetworkServiceImpl;
import com.justsyncit.network.client.TcpClient;
import com.justsyncit.network.server.TcpServer;
import com.justsyncit.network.connection.ConnectionManager;
import com.justsyncit.network.connection.ConnectionManagerImpl;
import com.justsyncit.network.transfer.FileTransferManager;
import com.justsyncit.network.transfer.FileTransferManagerImpl;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.FilesystemChunkIndex;
import com.justsyncit.storage.FilesystemContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.MetadataServiceFactory;

import java.io.IOException;

import com.justsyncit.hash.HashingException;

/**
 * Factory for creating application services and dependencies.
 * Follows Dependency Inversion Principle by providing abstractions.
 */
public class ServiceFactory {

    /**
     * Creates a fully configured JustSyncItApplicationRefactored.
     *
     * @return configured application instance
     * @throws ServiceException if application creation fails
     */
    public JustSyncItApplication createApplication() throws ServiceException {
        Blake3Service blake3Service = createBlake3Service();
        CommandRegistry commandRegistry = createCommandRegistry(blake3Service);
        ApplicationInfoDisplay infoDisplay = createInfoDisplay();

        return new JustSyncItApplication(blake3Service, commandRegistry, infoDisplay);
    }

    /**
     * Creates a BLAKE3 service with all dependencies.
     *
     * @return configured BLAKE3 service
     * @throws ServiceException if service creation fails
     */
    public Blake3Service createBlake3Service() throws ServiceException {
        try {
            // Create separate HashAlgorithm instances for each service to ensure thread
            // safety
            HashAlgorithm bufferHasherAlgorithm = Sha256HashAlgorithm.create();
            HashAlgorithm incrementalHasherAlgorithm = Sha256HashAlgorithm.create();

            BufferHasher bufferHasher = new Blake3BufferHasher(bufferHasherAlgorithm);
            IncrementalHasherFactory incrementalHasherFactory = new Blake3IncrementalHasherFactory(
                    incrementalHasherAlgorithm);
            StreamHasher streamHasher = new Blake3StreamHasher(incrementalHasherFactory);
            FileHasher fileHasher = new Blake3FileHasher(streamHasher, bufferHasher);
            SimdDetectionService simdDetectionService = new SimdDetectionServiceImpl();

            return new Blake3ServiceImpl(
                    fileHasher, bufferHasher, streamHasher,
                    incrementalHasherFactory, simdDetectionService);
        } catch (HashingException e) {
            throw new ServiceException("Failed to create BLAKE3 service", e);
        }
    }

    /**
     * Creates a content store with all dependencies.
     *
     * @param blake3Service BLAKE3 service for hashing
     * @return configured content store
     */
    public ContentStore createContentStore(Blake3Service blake3Service) throws IOException {
        java.nio.file.Path storageDir = java.nio.file.Paths.get("storage", "chunks");
        java.nio.file.Path indexFile = java.nio.file.Paths.get("storage", "index.txt");

        FilesystemChunkIndex chunkIndex = FilesystemChunkIndex.create(storageDir, indexFile);
        return FilesystemContentStore.create(storageDir, chunkIndex, blake3Service);
    }

    /**
     * Creates an encryption service.
     *
     * @return configured encryption service
     */
    public com.justsyncit.network.encryption.EncryptionService createEncryptionService() {
        return new com.justsyncit.network.encryption.AesGcmEncryptionService();
    }

    /**
     * Creates an encrypted content store.
     *
     * @param delegate          the underlying content store
     * @param encryptionService the encryption service
     * @param keySupplier       supplier for the encryption key
     * @param blake3Service     BLAKE3 service
     * @return configured encrypted content store
     */
    public ContentStore createEncryptedContentStore(ContentStore delegate,
            com.justsyncit.network.encryption.EncryptionService encryptionService,
            java.util.function.Supplier<byte[]> keySupplier,
            Blake3Service blake3Service) {
        return new com.justsyncit.storage.EncryptedContentStore(delegate, encryptionService, keySupplier,
                blake3Service);
    }

    /**
     * Creates an encrypted metadata service.
     *
     * @param databasePath      path to database
     * @param encryptionService encryption service
     * @param keySupplier       key supplier
     * @return configured metadata service
     */
    public MetadataService createEncryptedMetadataService(String databasePath,
            com.justsyncit.network.encryption.EncryptionService encryptionService,
            java.util.function.Supplier<byte[]> keySupplier) throws IOException {
        com.justsyncit.metadata.BlindIndexSearch blindIndexSearch = new com.justsyncit.metadata.BlindIndexSearch(
                keySupplier);
        return MetadataServiceFactory.createEncryptedFileBasedService(databasePath, encryptionService, blindIndexSearch,
                keySupplier);
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
        registry.register(new VerifyCommand()); // Uses CommandContext for injection
        registry.register(new com.justsyncit.command.DedupStatsCommand());
        try {
            registry.register(new com.justsyncit.command.DiffCommand(createMetadataService()));
        } catch (ServiceException e) {
            // Log error but allow startup? Or throw?
            // Since this is factory, we should probably just throw if metadata service
            // fails.
            // But createCommandRegistry signature doesn't throw.
            // Let's create metadata service outside or wrap exception.
            throw new RuntimeException("Failed to create metadata service for DiffCommand", e);
        }

        return registry;
    }

    /**
     * Creates a network service with all dependencies.
     *
     * @return configured network service
     */
    /**
     * Creates a network service with a new BLAKE3 service.
     * Maintained for backward compatibility and tests.
     *
     * @return configured network service
     */
    public NetworkService createNetworkService() throws ServiceException {
        return createNetworkService(createBlake3Service());
    }

    /**
     * Creates a network service with the provided BLAKE3 service.
     *
     * @param blake3Service the BLAKE3 service to use
     * @return configured network service
     */
    public NetworkService createNetworkService(Blake3Service blake3Service) {
        com.justsyncit.network.NetworkConfiguration configuration = new com.justsyncit.network.NetworkConfiguration();
        TcpServer tcpServer = new TcpServer(configuration);
        TcpClient tcpClient = new TcpClient(configuration);
        ConnectionManager connectionManager = new ConnectionManagerImpl();
        FileTransferManagerImpl fileTransferManager = new FileTransferManagerImpl();
        fileTransferManager.setBlake3Service(blake3Service);
        // Inject dependencies for DIP
        fileTransferManager.setTransferPipelineFactory(
                new com.justsyncit.network.transfer.pipeline.DefaultTransferPipelineFactory());

        return new NetworkServiceImpl(tcpServer, tcpClient, connectionManager, fileTransferManager, blake3Service);
    }

    /**
     * Creates a command registry with network commands.
     *
     * @param blake3Service  BLAKE3 service
     * @param networkService network service
     * @return configured command registry with network commands
     */
    public CommandRegistry createCommandRegistryWithNetwork(
            Blake3Service blake3Service, NetworkService networkService) throws ServiceException {
        CommandRegistry registry = createCommandRegistry(blake3Service);

        // Register network command
        try {
            registry.register(NetworkCommand.create(
                    networkService, createContentStore(blake3Service)));
        } catch (IOException e) {
            // Handle registration exception
            throw new ServiceException("Failed to register network command", e);
        } catch (RuntimeException e) {
            // Catch unexpected runtime exceptions
            throw new ServiceException("Unexpected error registering network command", e);
        }

        return registry;
    }

    /**
     * Creates a metadata service with default configuration.
     *
     * @return a metadata service instance
     * @throws ServiceException if service creation fails
     */
    public MetadataService createMetadataService() throws ServiceException {
        try {
            return MetadataServiceFactory.createDefaultService();
        } catch (IOException e) {
            throw new ServiceException("Failed to create metadata service", e);
        }
    }

    /**
     * Creates a backup service with all dependencies.
     *
     * @param contentStore    content store for storing chunks
     * @param metadataService metadata service for snapshot management
     * @param blake3Service   BLAKE3 service for integrity verification
     * @return a configured BackupService instance
     * @throws ServiceException if service creation fails
     */
    public com.justsyncit.backup.BackupService createBackupService(ContentStore contentStore,
            MetadataService metadataService,
            Blake3Service blake3Service) throws ServiceException {
        try {
            com.justsyncit.scanner.FilesystemScanner scanner = new com.justsyncit.scanner.NioFilesystemScanner();
            com.justsyncit.scanner.FileChunker chunker = com.justsyncit.scanner.FixedSizeFileChunker
                    .create(blake3Service);
            return new com.justsyncit.backup.BackupService(contentStore, metadataService, scanner, chunker, null,
                    blake3Service);
        } catch (HashingException e) {
            throw new ServiceException("Failed to create backup service", e);
        }
    }

    /**
     * Creates a restore service with all dependencies.
     *
     * @param contentStore    content store for retrieving chunks
     * @param metadataService metadata service for snapshot management
     * @param blake3Service   BLAKE3 service for integrity verification
     * @return a configured RestoreService instance
     * @throws ServiceException if service creation fails
     */
    public com.justsyncit.restore.RestoreService createRestoreService(ContentStore contentStore,
            MetadataService metadataService,
            Blake3Service blake3Service) throws ServiceException {
        return new com.justsyncit.restore.RestoreService(contentStore, metadataService, blake3Service);
    }

    /**
     * Creates a backup command with dependency injection.
     *
     * @param backupService backup service
     * @return a configured BackupCommand instance
     * @throws ServiceException if command creation fails
     */
    public com.justsyncit.command.BackupCommand createBackupCommand(com.justsyncit.backup.BackupService backupService)
            throws ServiceException {
        try {
            Blake3Service blake3Service = createBlake3Service();
            NetworkService networkService = createNetworkService(blake3Service);
            return new com.justsyncit.command.BackupCommand(backupService, networkService);
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create backup command", e);
        }
    }

    /**
     * Creates a backup command with network service injection.
     *
     * @param backupService  backup service
     * @param networkService network service
     * @return a configured BackupCommand instance
     * @throws ServiceException if command creation fails
     */
    public com.justsyncit.command.BackupCommand createBackupCommand(com.justsyncit.backup.BackupService backupService,
            NetworkService networkService) throws ServiceException {
        try {
            return new com.justsyncit.command.BackupCommand(backupService, networkService);
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create backup command", e);
        }
    }

    /**
     * Creates a restore command with dependency injection.
     *
     * @param restoreService restore service
     * @return a configured RestoreCommand instance
     * @throws ServiceException if command creation fails
     */
    public com.justsyncit.command.RestoreCommand createRestoreCommand(
            com.justsyncit.restore.RestoreService restoreService) throws ServiceException {
        try {
            Blake3Service blake3Service = createBlake3Service();
            NetworkService networkService = createNetworkService(blake3Service);
            return new com.justsyncit.command.RestoreCommand(restoreService, networkService);
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create restore command", e);
        }
    }

    /**
     * Creates a restore command with network service injection.
     *
     * @param restoreService restore service
     * @param networkService network service
     * @return a configured RestoreCommand instance
     * @throws ServiceException if command creation fails
     */
    public com.justsyncit.command.RestoreCommand createRestoreCommand(
            com.justsyncit.restore.RestoreService restoreService, NetworkService networkService)
            throws ServiceException {
        try {
            return new com.justsyncit.command.RestoreCommand(restoreService, networkService);
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create restore command", e);
        }
    }

    /**
     * Creates a metadata service with file-based database.
     *
     * @param databasePath path to SQLite database file
     * @return a metadata service instance
     * @throws ServiceException if service creation fails
     */
    public MetadataService createMetadataService(String databasePath) throws ServiceException {
        try {
            return MetadataServiceFactory.createFileBasedService(databasePath);
        } catch (IOException e) {
            throw new ServiceException("Failed to create metadata service", e);
        }
    }

    /**
     * Creates a metadata service with in-memory database for testing.
     *
     * @return a metadata service instance
     * @throws ServiceException if service creation fails
     */
    public MetadataService createInMemoryMetadataService() throws ServiceException {
        try {
            return MetadataServiceFactory.createInMemoryService();
        } catch (IOException e) {
            throw new ServiceException("Failed to create in-memory metadata service", e);
        }
    }

    /**
     * Creates a SQLite-enhanced content store with metadata service.
     *
     * @param blake3Service BLAKE3 service for hashing
     * @return a SQLite-enhanced content store instance
     * @throws ServiceException if store creation fails
     */
    public ContentStore createSqliteContentStore(Blake3Service blake3Service) throws ServiceException {
        try {
            MetadataService metadataService = createMetadataService();
            return com.justsyncit.storage.ContentStoreFactory.createDefaultSqliteStore(metadataService, blake3Service);
        } catch (IOException e) {
            throw new ServiceException("Failed to create SQLite content store", e);
        }
    }

    /**
     * Creates an application info display.
     *
     * @return info display instance
     */
    private ApplicationInfoDisplay createInfoDisplay() {
        return new ConsoleInfoDisplay();
    }

    /**
     * Creates an async backup service with all dependencies.
     *
     * @param contentStore    content store for storing chunks
     * @param metadataService metadata service for snapshot management
     * @param blake3Service   BLAKE3 service for integrity verification
     * @return a configured async BackupService instance
     * @throws ServiceException if service creation fails
     */
    public com.justsyncit.backup.BackupService createAsyncBackupService(ContentStore contentStore,
            MetadataService metadataService,
            Blake3Service blake3Service) throws ServiceException {
        try {
            // Create async components
            com.justsyncit.scanner.AsyncByteBufferPool bufferPool = com.justsyncit.scanner.AsyncByteBufferPoolImpl
                    .create();
            com.justsyncit.scanner.ThreadPoolManager threadPoolManager = com.justsyncit.scanner.ThreadPoolManager
                    .getInstance();
            com.justsyncit.scanner.AsyncFilesystemScanner asyncScanner = new com.justsyncit.scanner.AsyncFilesystemScannerImpl(
                    threadPoolManager, bufferPool);
            com.justsyncit.scanner.AsyncFileChunker asyncChunker = com.justsyncit.scanner.AsyncFileChunkerImpl
                    .create(blake3Service);

            return new com.justsyncit.backup.BackupService(contentStore, metadataService, asyncScanner, asyncChunker,
                    null, blake3Service);
        } catch (HashingException e) {
            throw new ServiceException("Failed to create async backup service", e);
        }
    }

    /**
     * Creates an async backup service with batch processing capabilities.
     *
     * @param contentStore    content store for storing chunks
     * @param metadataService metadata service for snapshot management
     * @param blake3Service   BLAKE3 service for integrity verification
     * @return a configured async BackupService instance with batch processing
     * @throws ServiceException if service creation fails
     */
    public com.justsyncit.backup.BackupService createBatchAsyncBackupService(ContentStore contentStore,
            MetadataService metadataService,
            Blake3Service blake3Service) throws ServiceException {
        try {
            // Create async components with batch processing
            com.justsyncit.scanner.AsyncByteBufferPool bufferPool = com.justsyncit.scanner.AsyncByteBufferPoolImpl
                    .create();
            com.justsyncit.scanner.ThreadPoolManager threadPoolManager = com.justsyncit.scanner.ThreadPoolManager
                    .getInstance();
            com.justsyncit.scanner.AsyncFilesystemScanner asyncScanner = new com.justsyncit.scanner.AsyncFilesystemScannerImpl(
                    threadPoolManager, bufferPool);
            com.justsyncit.scanner.AsyncFileChunker delegateChunker = com.justsyncit.scanner.AsyncFileChunkerImpl
                    .create(blake3Service);
            com.justsyncit.scanner.AsyncBatchProcessor batchProcessor = com.justsyncit.scanner.AsyncFileBatchProcessorImpl
                    .create(delegateChunker, bufferPool, threadPoolManager);
            com.justsyncit.scanner.BatchConfiguration batchConfig = new com.justsyncit.scanner.BatchConfiguration();
            com.justsyncit.scanner.AsyncFileChunker batchAsyncChunker = new com.justsyncit.scanner.BatchAwareAsyncFileChunker(
                    delegateChunker, batchProcessor, batchConfig);

            return new com.justsyncit.backup.BackupService(contentStore, metadataService, asyncScanner,
                    batchAsyncChunker, null, blake3Service);
        } catch (HashingException e) {
            throw new ServiceException("Failed to create batch async backup service", e);
        }
    }

    /**
     * Creates an async filesystem scanner.
     *
     * @return configured async filesystem scanner
     * @throws ServiceException if scanner creation fails
     */
    public com.justsyncit.scanner.AsyncFilesystemScanner createAsyncFilesystemScanner() throws ServiceException {
        try {
            com.justsyncit.scanner.AsyncByteBufferPool bufferPool = com.justsyncit.scanner.AsyncByteBufferPoolImpl
                    .create();
            com.justsyncit.scanner.ThreadPoolManager threadPoolManager = com.justsyncit.scanner.ThreadPoolManager
                    .getInstance();
            return new com.justsyncit.scanner.AsyncFilesystemScannerImpl(threadPoolManager, bufferPool);
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create async filesystem scanner", e);
        }
    }

    /**
     * Creates an async file chunker.
     *
     * @param blake3Service BLAKE3 service for hashing
     * @return configured async file chunker
     * @throws ServiceException if chunker creation fails
     */
    public com.justsyncit.scanner.AsyncFileChunker createAsyncFileChunker(Blake3Service blake3Service)
            throws ServiceException {
        try {

            return com.justsyncit.scanner.AsyncFileChunkerImpl.create(blake3Service);
        } catch (HashingException e) {
            throw new ServiceException("Failed to create async file chunker", e);
        }
    }

    /**
     * Creates a FastCDC file chunker.
     *
     * @param blake3Service BLAKE3 service for hashing
     * @return configured FastCDC file chunker
     * @throws ServiceException if chunker creation fails
     */
    public com.justsyncit.scanner.FileChunker createFastCDCFileChunker(Blake3Service blake3Service)
            throws ServiceException {
        try {
            return com.justsyncit.scanner.FastCDCFileChunker.create(blake3Service);
        } catch (Exception e) {
            throw new ServiceException("Failed to create FastCDC file chunker", e);
        }
    }

    /**
     * Creates a file chunker based on the provided algorithm.
     *
     * @param blake3Service BLAKE3 service for hashing
     * @param algorithm     the chunking algorithm to use
     * @return configured file chunker
     * @throws ServiceException if chunker creation fails
     */
    public com.justsyncit.scanner.FileChunker createFileChunker(Blake3Service blake3Service,
            com.justsyncit.scanner.ChunkingOptions.ChunkingAlgorithm algorithm) throws ServiceException {
        if (algorithm == com.justsyncit.scanner.ChunkingOptions.ChunkingAlgorithm.CDC) {
            return createFastCDCFileChunker(blake3Service);
        } else {
            return com.justsyncit.scanner.FixedSizeFileChunker.create(blake3Service);
        }
    }

    /**
     * Creates a batch-aware async file chunker.
     *
     * @param blake3Service BLAKE3 service for hashing
     * @return configured batch-aware async file chunker
     * @throws ServiceException if chunker creation fails
     */
    public com.justsyncit.scanner.AsyncFileChunker createBatchAsyncFileChunker(Blake3Service blake3Service)
            throws ServiceException {
        try {
            com.justsyncit.scanner.AsyncByteBufferPool bufferPool = com.justsyncit.scanner.AsyncByteBufferPoolImpl
                    .create();
            com.justsyncit.scanner.ThreadPoolManager threadPoolManager = com.justsyncit.scanner.ThreadPoolManager
                    .getInstance();
            com.justsyncit.scanner.AsyncFileChunker delegateChunker = com.justsyncit.scanner.AsyncFileChunkerImpl
                    .create(blake3Service);
            com.justsyncit.scanner.AsyncBatchProcessor batchProcessor = com.justsyncit.scanner.AsyncFileBatchProcessorImpl
                    .create(delegateChunker, bufferPool, threadPoolManager);
            com.justsyncit.scanner.BatchConfiguration batchConfig = new com.justsyncit.scanner.BatchConfiguration();
            return new com.justsyncit.scanner.BatchAwareAsyncFileChunker(delegateChunker, batchProcessor, batchConfig);
        } catch (HashingException e) {
            throw new ServiceException("Failed to create batch async file chunker", e);
        }
    }

    /**
     * Creates an async buffer pool.
     *
     * @return configured async buffer pool
     * @throws ServiceException if pool creation fails
     */
    public com.justsyncit.scanner.AsyncByteBufferPool createAsyncByteBufferPool() throws ServiceException {
        try {
            return com.justsyncit.scanner.AsyncByteBufferPoolImpl.create();
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create async buffer pool", e);
        }
    }

    /**
     * Creates a thread pool manager.
     *
     * @return configured thread pool manager
     * @throws ServiceException if manager creation fails
     */
    public com.justsyncit.scanner.ThreadPoolManager createThreadPoolManager() throws ServiceException {
        try {
            return com.justsyncit.scanner.ThreadPoolManager.getInstance();
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create thread pool manager", e);
        }
    }

    /**
     * Creates a scheduler service.
     *
     * @param backupService the backup service
     * @return configured scheduler service
     */
    public com.justsyncit.scheduler.SchedulerService createSchedulerService(
            com.justsyncit.backup.BackupService backupService) {
        return new com.justsyncit.scheduler.SchedulerService(backupService);
    }
}