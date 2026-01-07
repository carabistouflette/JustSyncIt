package com.justsyncit.storage;

import com.justsyncit.ServiceException;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.modules.StorageModule;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.MetadataServiceFactory;
import com.justsyncit.network.encryption.EncryptionService;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Factory for creating storage-related services.
 * Extracts storage responsibility from the monolithic ServiceFactory.
 */
public class StorageFactory {

    private final StorageModule storageModule;

    public StorageFactory() {
        this.storageModule = new StorageModule();
    }

    public ContentStore createContentStore(Blake3Service blake3Service) throws IOException {
        return storageModule.createContentStore(blake3Service);
    }

    public ContentStore createSqliteContentStore(Blake3Service blake3Service) throws ServiceException {
        return storageModule.createSqliteContentStore(blake3Service);
    }

    public ContentStore createEncryptedContentStore(ContentStore delegate,
            EncryptionService encryptionService,
            Supplier<byte[]> keySupplier,
            Blake3Service blake3Service) {
        return new EncryptedContentStore(delegate, encryptionService, keySupplier, blake3Service);
    }

    public MetadataService createMetadataService() throws ServiceException {
        try {
            return MetadataServiceFactory.createDefaultService();
        } catch (IOException e) {
            throw new ServiceException("Failed to create metadata service", e);
        }
    }

    public MetadataService createMetadataService(String databasePath) throws ServiceException {
        try {
            return MetadataServiceFactory.createFileBasedService(databasePath);
        } catch (IOException e) {
            throw new ServiceException("Failed to create metadata service", e);
        }
    }

    public MetadataService createInMemoryMetadataService() throws ServiceException {
        try {
            return MetadataServiceFactory.createInMemoryService();
        } catch (IOException e) {
            throw new ServiceException("Failed to create in-memory metadata service", e);
        }
    }

    public MetadataService createEncryptedMetadataService(String databasePath,
            EncryptionService encryptionService,
            Supplier<byte[]> keySupplier) throws IOException {
        com.justsyncit.metadata.BlindIndexSearch blindIndexSearch = new com.justsyncit.metadata.BlindIndexSearch(
                keySupplier);
        return MetadataServiceFactory.createEncryptedFileBasedService(databasePath, encryptionService, blindIndexSearch,
                keySupplier);
    }
}
