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

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.NetworkService;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
    void testCreateBlake3Service() {
        assertDoesNotThrow(() -> {
            Blake3Service blake3Service = serviceFactory.createBlake3Service();
            assertNotNull(blake3Service);
        });
    }

    @Test
    void testCreateNetworkService() {
        NetworkService networkService = serviceFactory.createNetworkService();
        assertNotNull(networkService);
    }

    @Test
    void testCreateContentStore() {
        assertDoesNotThrow(() -> {
            Blake3Service blake3Service = serviceFactory.createBlake3Service();
            ContentStore contentStore = serviceFactory.createContentStore(blake3Service);
            assertNotNull(contentStore);
        });
    }

    @Test
    void testCreateMetadataService() {
        assertDoesNotThrow(() -> {
            MetadataService metadataService = serviceFactory.createMetadataService();
            assertNotNull(metadataService);
        });
    }

    @Test
    void testCreateInMemoryMetadataService() {
        assertDoesNotThrow(() -> {
            MetadataService metadataService = serviceFactory.createInMemoryMetadataService();
            assertNotNull(metadataService);
        });
    }

    @Test
    void testCreateSqliteContentStore() {
        assertDoesNotThrow(() -> {
            Blake3Service blake3Service = serviceFactory.createBlake3Service();
            ContentStore contentStore = serviceFactory.createSqliteContentStore(blake3Service);
            assertNotNull(contentStore);
        });
    }

    @Test
    void testCreateApplication() {
        assertDoesNotThrow(() -> {
            JustSyncItApplication app = serviceFactory.createApplication();
            assertNotNull(app);
        });
    }

    @Test
    void testServiceFactoryNotNull() {
        assertNotNull(serviceFactory);
    }
}