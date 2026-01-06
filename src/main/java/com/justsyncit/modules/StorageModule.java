package com.justsyncit.modules;

import com.justsyncit.ServiceException;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.FilesystemChunkIndex;
import com.justsyncit.storage.FilesystemContentStore;
import com.justsyncit.storage.HealingContentStore;
import com.justsyncit.integrity.service.ReedSolomonService;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.storage.metadata.MetadataServiceFactory;
import java.io.IOException;

/**
 * Module responsible for creating storage-related services.
 */
public class StorageModule {

    private ContentStore sharedContentStore;

    public synchronized ContentStore createContentStore(Blake3Service blake3Service) throws IOException {
        if (sharedContentStore == null) {
            java.nio.file.Path storageDir = java.nio.file.Paths.get("storage", "chunks");
            java.nio.file.Path indexFile = java.nio.file.Paths.get("storage", "index.txt");

            FilesystemChunkIndex chunkIndex = FilesystemChunkIndex.create(storageDir, indexFile);
            sharedContentStore = FilesystemContentStore.create(storageDir, chunkIndex, blake3Service);
        }
        return sharedContentStore;
    }

    public MetadataService createMetadataService(String databasePath) throws ServiceException {
        try {
            return MetadataServiceFactory.createFileBasedService(databasePath);
        } catch (IOException e) {
            throw new ServiceException("Failed to create metadata service", e);
        }
    }

    public MetadataService createMetadataService() throws ServiceException {
        try {
            return MetadataServiceFactory.createDefaultService();
        } catch (IOException e) {
            throw new ServiceException("Failed to create metadata service", e);
        }
    }

    public ContentStore createSqliteContentStore(Blake3Service blake3Service) throws ServiceException {
        try {
            MetadataService metadataService = createMetadataService();
            // Create raw store using singleton
            ContentStore fsStore = createContentStore(blake3Service);
            ContentStore rawStore = com.justsyncit.storage.ContentStoreFactory.createSqliteStore(fsStore,
                    metadataService);

            // Wire up self-healing
            ReedSolomonService rsService = new ReedSolomonService(metadataService, rawStore, blake3Service, 4, 2);

            return new HealingContentStore(rawStore, rsService);
        } catch (IOException e) {
            throw new ServiceException("Failed to create SQLite content store", e);
        }
    }
}
