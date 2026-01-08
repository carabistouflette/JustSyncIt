package com.justsyncit;

import java.io.IOException;
import java.util.function.Supplier;

import com.justsyncit.backup.BackupService;
import com.justsyncit.command.BackupCommand;
import com.justsyncit.command.CommandContext;
import com.justsyncit.command.CommandRegistry;
import com.justsyncit.command.RestoreCommand;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.integrity.service.IntegrityCheckService;
import com.justsyncit.integrity.service.ReedSolomonService;
import com.justsyncit.modules.CommandModule;
import com.justsyncit.modules.ScannerModule;
import com.justsyncit.modules.SecurityModule;
import com.justsyncit.network.NetworkFactory;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.encryption.EncryptionService;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.scheduler.SchedulerService;
import com.justsyncit.scanner.AsyncByteBufferPool;
import com.justsyncit.scanner.AsyncFileChunker;
import com.justsyncit.scanner.AsyncFilesystemScanner;
import com.justsyncit.scanner.ChunkingOptions;
import com.justsyncit.scanner.FileChunker;
import com.justsyncit.scanner.ThreadPoolManager;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.ContentStoreFactory;
import com.justsyncit.storage.HealingContentStore;
import com.justsyncit.storage.StorageFactory;
import com.justsyncit.storage.metadata.MetadataService;

/**
 * Factory for creating application services and managing dependencies.
 * Now acts as a facade delegating to domain-specific factories and modules.
 */
public class ServiceFactory {

    private final SecurityModule securityModule;
    private final ScannerModule scannerModule;
    private final CommandModule commandModule;
    private final StorageFactory storageFactory;
    private final NetworkFactory networkFactory;

    // Singleton instances
    private SchedulerService schedulerService;
    private BackupService backupService;
    private RestoreService restoreService;
    private MetadataService metadataService;
    private com.justsyncit.auth.MasterPasswordService masterPasswordService;

    public ServiceFactory() {
        this.securityModule = new SecurityModule();
        this.scannerModule = new ScannerModule();
        this.commandModule = new CommandModule();
        this.storageFactory = new StorageFactory();
        this.networkFactory = new NetworkFactory(this.securityModule);
        this.networkFactory.setMasterPasswordService(createMasterPasswordService());
    }

    /**
     * Creates a fully configured JustSyncItApplicationRefactored.
     */
    public JustSyncItApplication createApplication() throws ServiceException {
        Blake3Service blake3Service = createBlake3Service();
        ApplicationInfoDisplay infoDisplay = commandModule.createInfoDisplay();
        CommandContext commandContext = createDefaultCommandContext(blake3Service);

        // CommandRegistry now requires fully wired services
        // We need to create a registry and manually access the wired services from
        // context or factory
        // But createCommandRegistry expects explicit services.

        // Strategy: Create Context First, then extract services to create Registry.
        CommandRegistry commandRegistry;
        try {
            MetadataService metadata = commandContext.getMetadataService();
            IntegrityCheckService integrity = createIntegrityCheckService(metadata, blake3Service);
            commandRegistry = commandModule.createCommandRegistry(blake3Service, metadata, integrity);
        } catch (ServiceException e) {
            throw new RuntimeException("Failed to wire command registry", e);
        }

        return new JustSyncItApplication(commandContext, commandRegistry, infoDisplay);
    }

    public CommandContext createDefaultCommandContext(Blake3Service blake3Service) throws ServiceException {
        try {
            MetadataService metadataService = createMetadataService();
            ContentStore contentStore = createContentStore(blake3Service);
            RestoreService restoreService = createRestoreService(contentStore, metadataService, blake3Service);
            BackupService backupService = createAsyncBackupService(contentStore, metadataService, blake3Service);

            // Create additional services for Web/Full context
            SchedulerService scheduler = createSchedulerService(backupService);
            NetworkService networkService = createNetworkService(blake3Service);

            return CommandContext.builder(blake3Service)
                    .metadataService(metadataService)
                    .contentStore(contentStore)
                    .restoreService(restoreService)
                    .backupService(backupService)
                    .schedulerService(scheduler)
                    .networkService(networkService)
                    .masterPasswordService(createMasterPasswordService())
                    .build();
        } catch (IOException e) {
            throw new ServiceException("Failed to create default command context", e);
        }
    }

    // --- Security Module Delegates ---

    public Blake3Service createBlake3Service() throws ServiceException {
        return securityModule.createBlake3Service();
    }

    public EncryptionService createEncryptionService() {
        return securityModule.createEncryptionService();
    }

    // --- Storage Factory Delegates ---

    public synchronized ContentStore createContentStore(Blake3Service blake3Service) throws IOException {
        return storageFactory.createContentStore(blake3Service);
    }

    public ContentStore createEncryptedContentStore(ContentStore delegate,
            EncryptionService encryptionService,
            Supplier<byte[]> keySupplier,
            Blake3Service blake3Service) {
        return storageFactory.createEncryptedContentStore(delegate, encryptionService, keySupplier, blake3Service);
    }

    public MetadataService createEncryptedMetadataService(String databasePath,
            EncryptionService encryptionService,
            Supplier<byte[]> keySupplier) throws IOException {
        return storageFactory.createEncryptedMetadataService(databasePath, encryptionService, keySupplier);
    }

    public synchronized MetadataService createMetadataService() throws ServiceException {
        if (metadataService == null) {
            metadataService = storageFactory.createMetadataService();
        }
        return metadataService;
    }

    public synchronized com.justsyncit.auth.MasterPasswordService createMasterPasswordService() {
        if (masterPasswordService == null) {
            masterPasswordService = new com.justsyncit.auth.MasterPasswordService();
        }
        return masterPasswordService;
    }

    public MetadataService createMetadataService(String databasePath) throws ServiceException {
        return storageFactory.createMetadataService(databasePath);
    }

    public MetadataService createInMemoryMetadataService() throws ServiceException {
        return storageFactory.createInMemoryMetadataService();
    }

    public ContentStore createSqliteContentStore(Blake3Service blake3Service) throws ServiceException {
        return storageFactory.createSqliteContentStore(blake3Service);
    }

    // --- Network Factory Delegates ---

    public NetworkService createNetworkService() throws ServiceException {
        return createNetworkService(createBlake3Service());
    }

    public NetworkService createNetworkService(Blake3Service blake3Service) {
        try {
            return networkFactory.createNetworkService(blake3Service, createMetadataService());
        } catch (ServiceException e) {
            throw new RuntimeException("Failed to obtain MetadataService for NetworkService", e);
        }
    }

    public NetworkService createNetworkService(Blake3Service blake3Service, String clusterKey) {
        try {
            return networkFactory.createNetworkService(blake3Service, clusterKey, createMetadataService());
        } catch (ServiceException e) {
            throw new RuntimeException("Failed to obtain MetadataService for NetworkService", e);
        }
    }

    // --- Core Services (Backup/Restore/Integrity) ---

    public synchronized BackupService createBackupService(ContentStore contentStore,
            MetadataService metadataService,
            Blake3Service blake3Service) throws ServiceException {
        // Fallback to async implementation as default
        return createAsyncBackupService(contentStore, metadataService, blake3Service);
    }

    public synchronized RestoreService createRestoreService(ContentStore contentStore,
            MetadataService metadataService,
            Blake3Service blake3Service) throws ServiceException {
        if (restoreService == null) {
            restoreService = new RestoreService(contentStore, metadataService, blake3Service);
        }
        return restoreService;
    }

    public synchronized SchedulerService createSchedulerService(BackupService backupService) {
        if (schedulerService == null) {
            schedulerService = new SchedulerService(backupService);
        }
        return schedulerService;
    }

    public IntegrityCheckService createIntegrityCheckService(MetadataService metadataService,
            Blake3Service blake3Service) throws ServiceException {
        try {
            // Reuse the existing filesystem store
            ContentStore fsStore = createContentStore(blake3Service);
            ContentStore rawStore = ContentStoreFactory.createSqliteStore(fsStore, metadataService);

            ReedSolomonService rsService = new ReedSolomonService(metadataService, rawStore, blake3Service, 4, 2);
            HealingContentStore healingStore = new HealingContentStore(rawStore, rsService);

            return new IntegrityCheckService(healingStore, metadataService, rsService);
        } catch (IOException e) {
            throw new ServiceException("Failed to create integrity check service", e);
        }
    }

    // --- Scanner Module Delegates (Async & Chunking) ---

    public synchronized BackupService createAsyncBackupService(ContentStore contentStore,
            MetadataService metadataService,
            Blake3Service blake3Service) throws ServiceException {
        if (backupService == null) {
            try {
                AsyncFilesystemScanner asyncScanner = scannerModule.createAsyncFilesystemScanner();
                AsyncFileChunker asyncChunker = scannerModule.createAsyncFileChunker(blake3Service);

                backupService = new BackupService(
                        contentStore,
                        metadataService,
                        asyncScanner,
                        asyncChunker,
                        null,
                        blake3Service);
            } catch (Exception e) {
                throw new ServiceException("Failed to create async backup service", e);
            }
        }
        return backupService;
    }

    public BackupService createBatchAsyncBackupService(ContentStore contentStore,
            MetadataService metadataService,
            Blake3Service blake3Service) throws ServiceException {
        try {
            // Create standard async scanner
            AsyncFilesystemScanner asyncScanner = scannerModule.createAsyncFilesystemScanner();
            // Create specalized batch chunker
            AsyncFileChunker batchAsyncChunker = scannerModule.createBatchAsyncFileChunker(blake3Service);

            return new BackupService(contentStore, metadataService, asyncScanner,
                    batchAsyncChunker, null, blake3Service);
        } catch (Exception e) {
            throw new ServiceException("Failed to create batch async backup service", e);
        }
    }

    public AsyncFilesystemScanner createAsyncFilesystemScanner() throws ServiceException {
        return scannerModule.createAsyncFilesystemScanner();
    }

    public AsyncFileChunker createAsyncFileChunker(Blake3Service blake3Service) throws ServiceException {
        return scannerModule.createAsyncFileChunker(blake3Service);
    }

    public FileChunker createFastCDCFileChunker(Blake3Service blake3Service) throws ServiceException {
        return scannerModule.createFastCDCFileChunker(blake3Service);
    }

    public FileChunker createFileChunker(Blake3Service blake3Service, ChunkingOptions.ChunkingAlgorithm algorithm)
            throws ServiceException {
        return scannerModule.createFileChunker(blake3Service, algorithm);
    }

    public AsyncFileChunker createBatchAsyncFileChunker(Blake3Service blake3Service) throws ServiceException {
        return scannerModule.createBatchAsyncFileChunker(blake3Service);
    }

    public AsyncByteBufferPool createAsyncByteBufferPool() throws ServiceException {
        return scannerModule.createAsyncByteBufferPool();
    }

    public ThreadPoolManager createThreadPoolManager() throws ServiceException {
        return scannerModule.createThreadPoolManager();
    }

    // --- Command Module Delegates ---

    public CommandRegistry createCommandRegistryWithNetwork(
            Blake3Service blake3Service, NetworkService networkService) throws ServiceException {
        try {
            // Needed dependencies
            ContentStore contentStore = createContentStore(blake3Service);
            MetadataService metadataService = createMetadataService();
            IntegrityCheckService integrityService = createIntegrityCheckService(metadataService, blake3Service);

            return commandModule.createCommandRegistryWithNetwork(blake3Service, networkService, contentStore,
                    metadataService, integrityService);
        } catch (IOException e) {
            throw new ServiceException("Failed to wire network registry", e);
        }
    }

    public BackupCommand createBackupCommand(BackupService backupService) throws ServiceException {
        NetworkService networkService = createNetworkService();
        return commandModule.createBackupCommand(backupService, networkService);
    }

    public BackupCommand createBackupCommand(BackupService backupService, NetworkService networkService)
            throws ServiceException {
        return commandModule.createBackupCommand(backupService, networkService);
    }

    public RestoreCommand createRestoreCommand(RestoreService restoreService) throws ServiceException {
        NetworkService networkService = createNetworkService();
        return commandModule.createRestoreCommand(restoreService, networkService);
    }

    public RestoreCommand createRestoreCommand(RestoreService restoreService, NetworkService networkService)
            throws ServiceException {
        return commandModule.createRestoreCommand(restoreService, networkService);
    }
}