package com.justsyncit;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.NetworkService;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for ServiceFactory.
 */
public class ServiceFactoryTest {

    private ServiceFactory serviceFactory;

    @BeforeEach
    void setUp() {
        serviceFactory = new ServiceFactory();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCreateBlake3Service() {
        assertDoesNotThrow(() -> {
            Blake3Service blake3Service = serviceFactory.createBlake3Service();
            assertNotNull(blake3Service);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCreateNetworkService() {
        assertDoesNotThrow(() -> {
            NetworkService networkService = serviceFactory.createNetworkService();
            assertNotNull(networkService);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCreateContentStore() {
        assertDoesNotThrow(() -> {
            Blake3Service blake3Service = serviceFactory.createBlake3Service();
            ContentStore contentStore = serviceFactory.createContentStore(blake3Service);
            assertNotNull(contentStore);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCreateMetadataService() {
        assertDoesNotThrow(() -> {
            MetadataService metadataService = serviceFactory.createMetadataService();
            assertNotNull(metadataService);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCreateInMemoryMetadataService() {
        assertDoesNotThrow(() -> {
            MetadataService metadataService = serviceFactory.createInMemoryMetadataService();
            assertNotNull(metadataService);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCreateSqliteContentStore() {
        assertDoesNotThrow(() -> {
            Blake3Service blake3Service = serviceFactory.createBlake3Service();
            ContentStore contentStore = serviceFactory.createSqliteContentStore(blake3Service);
            assertNotNull(contentStore);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testCreateApplication() {
        assertDoesNotThrow(() -> {
            JustSyncItApplication app = serviceFactory.createApplication();
            assertNotNull(app);
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testServiceFactoryNotNull() {
        assertNotNull(serviceFactory);
    }
}