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

package com.justsyncit.integration;

import com.justsyncit.ServiceFactory;
import com.justsyncit.backup.BackupService;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.integration.util.NetworkSimulationUtil;
import com.justsyncit.integration.util.TestDataGenerator;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for E2E integration tests providing common infrastructure.
 * Provides test isolation, service setup, and utility methods.
 */
public abstract class E2ETestBase {

    @TempDir
    protected Path tempDir;

    @TempDir
    protected Path storageDir;

    @TempDir
    protected Path sourceDir;

    @TempDir
    protected Path restoreDir;

    protected ServiceFactory serviceFactory;
    protected Blake3Service blake3Service;
    protected ContentStore contentStore;
    protected MetadataService metadataService;
    protected BackupService backupService;
    protected RestoreService restoreService;
    protected NetworkService networkService;
    protected NetworkService networkServiceWithSimulation;

    protected List<ResourceWrapper<?>> resourcesToCleanup = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        serviceFactory = new ServiceFactory();
        blake3Service = serviceFactory.createBlake3Service();
        contentStore = serviceFactory.createSqliteContentStore(blake3Service);
        metadataService = serviceFactory.createMetadataService(tempDir.resolve("test-metadata.db").toString());
        backupService = serviceFactory.createBackupService(contentStore, metadataService, blake3Service);
        restoreService = serviceFactory.createRestoreService(contentStore, metadataService, blake3Service);
        networkService = serviceFactory.createNetworkService();
        networkServiceWithSimulation = NetworkSimulationUtil.createExcellentNetworkSimulator(networkService);

        resourcesToCleanup.add(new ResourceWrapper<>(contentStore));
        resourcesToCleanup.add(new ResourceWrapper<>(metadataService));
        resourcesToCleanup.add(new ResourceWrapper<>(networkService));
    }

    @AfterEach
    void tearDown() throws Exception {
        // Wait for async operations to complete before cleanup
        waitForAsyncOperations(2000);

        // Clean up resources in reverse order of creation
        for (int i = resourcesToCleanup.size() - 1; i >= 0; i--) {
            try {
                ResourceWrapper<?> wrapper = resourcesToCleanup.get(i);
                if (wrapper != null) {
                    wrapper.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing resource: " + e.getMessage());
                e.printStackTrace();
            }
        }
        resourcesToCleanup.clear();

        // Additional cleanup wait
        waitForAsyncOperations(1000);
    }

    protected void waitForAsyncOperations(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Test data creation methods
    protected void createBasicDataset() throws IOException {
        TestDataGenerator.createBasicDataset(sourceDir);
    }

    protected void createDuplicateDataset() throws IOException {
        TestDataGenerator.createDuplicateDataset(sourceDir);
    }

    protected void createSpecialCharacterDataset() throws IOException {
        TestDataGenerator.createSpecialCharacterDataset(sourceDir);
    }

    protected void createPerformanceDataset(int fileCount, int maxFileSize) throws IOException {
        TestDataGenerator.createPerformanceDataset(sourceDir, fileCount, maxFileSize);
    }

    protected void createEmptyDataset() throws IOException {
        TestDataGenerator.createEmptyDataset(sourceDir);
    }

    protected void createPermissionDataset() throws IOException {
        TestDataGenerator.createPermissionDataset(sourceDir);
    }

    /**
     * Cleans up a directory by removing all files.
     */
    protected void cleanupDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }

    // Backup and restore utility methods
    protected String performBackup(String snapshotName) throws Exception {
        return performBackup(snapshotName, null);
    }

    protected String performBackup(String snapshotName, String description) throws Exception {
        com.justsyncit.backup.BackupOptions backupOptions = new com.justsyncit.backup.BackupOptions.Builder()
                .snapshotName(snapshotName)
                .description(description)
                .verifyIntegrity(true)
                .build();

        CompletableFuture<BackupService.BackupResult> backupFuture = backupService.backup(sourceDir, backupOptions);
        BackupService.BackupResult backupResult = backupFuture.get();

        assertTrue(backupResult.isSuccess(), "Backup should succeed");
        assertNotNull(backupResult.getSnapshotId(), "Snapshot ID should not be null");
        assertTrue(backupResult.getFilesProcessed() > 0, "Should process at least one file");
        assertTrue(backupResult.getTotalBytesProcessed() > 0, "Should process some bytes");
        assertTrue(backupResult.getChunksCreated() > 0, "Should create some chunks");
        assertTrue(backupResult.isIntegrityVerified(), "Integrity should be verified");

        return backupResult.getSnapshotId();
    }

    protected RestoreService.RestoreResult performRestore(String snapshotId) throws Exception {
        return performRestore(snapshotId, false);
    }

    protected RestoreService.RestoreResult performRestore(String snapshotId, boolean overwrite) throws Exception {
        com.justsyncit.restore.RestoreOptions restoreOptions = new com.justsyncit.restore.RestoreOptions.Builder()
                .overwriteExisting(overwrite)
                .verifyIntegrity(true)
                .build();

        CompletableFuture<RestoreService.RestoreResult> restoreFuture = restoreService.restore(snapshotId, restoreDir,
                restoreOptions);
        RestoreService.RestoreResult restoreResult = restoreFuture.get();

        assertTrue(restoreResult.isSuccess(), "Restore should succeed");
        assertTrue(restoreResult.getFilesRestored() > 0, "Should restore at least one file");
        assertTrue(restoreResult.getTotalBytesRestored() > 0, "Should restore some bytes");
        assertTrue(restoreResult.isIntegrityVerified(), "Integrity should be verified");

        return restoreResult;
    }

    // Network utility methods
    protected int findAvailablePort() {
        return NetworkSimulationUtil.findAvailablePort();
    }

    protected boolean waitForServerReady(String host, int port, long timeoutMs) {
        return NetworkSimulationUtil.waitForServerReady(host, port, timeoutMs);
    }

    protected boolean validateConnectivity(String host, int port) {
        return NetworkSimulationUtil.validateConnectivity(host, port);
    }

    // Assertion helpers
    protected void assertDirectoryStructureEquals(Path expected, Path actual) throws IOException {
        assertTrue(Files.exists(actual), "Target directory should exist");
        assertTrue(Files.isDirectory(actual), "Target should be a directory");

        // Count files recursively
        long expectedFiles = Files.walk(expected)
                .filter(Files::isRegularFile)
                .count();

        long actualFiles = Files.walk(actual)
                .filter(Files::isRegularFile)
                .count();

        assertEquals(expectedFiles, actualFiles, "File count should match");
    }

    protected void assertFileContentEquals(Path expectedFile, Path actualFile) throws IOException {
        assertTrue(Files.exists(expectedFile), "Expected file should exist");
        assertTrue(Files.exists(actualFile), "Actual file should exist");

        byte[] expectedBytes = Files.readAllBytes(expectedFile);
        byte[] actualBytes = Files.readAllBytes(actualFile);

        assertArrayEquals(expectedBytes, actualBytes, "File content should match");
    }

    protected void assertFileCount(int expected, Path directory) throws IOException {
        long actualCount = Files.walk(directory)
                .filter(Files::isRegularFile)
                .count();
        assertEquals(expected, actualCount, "File count should match");
    }

    protected void assertSnapshotExists(String snapshotId) throws IOException {
        assertTrue(metadataService.getSnapshot(snapshotId).isPresent(),
                "Snapshot should exist: " + snapshotId);
    }

    protected void assertSnapshotDoesNotExist(String snapshotId) throws IOException {
        assertFalse(metadataService.getSnapshot(snapshotId).isPresent(),
                "Snapshot should not exist: " + snapshotId);
    }

    // Performance measurement helpers
    protected long measureTime(Runnable operation) {
        long startTime = System.currentTimeMillis();
        operation.run();
        return System.currentTimeMillis() - startTime;
    }

    protected double calculateThroughput(long bytes, long timeMs) {
        if (timeMs == 0)
            return 0;
        return (bytes / 1024.0 / 1024.0) / (timeMs / 1000.0); // MB/s
    }

    // Resource management
    protected <T extends java.io.Closeable> T registerResource(T resource) {
        resourcesToCleanup.add(new ResourceWrapper<>(resource));
        return resource;
    }

    /**
     * Wrapper for resources that don't implement Closeable but have close()
     * methods.
     */
    private static class ResourceWrapper<T> implements java.io.Closeable {
        private final T resource;

        public ResourceWrapper(T resource) {
            this.resource = resource;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void close() throws IOException {
            try {
                if (resource instanceof com.justsyncit.storage.ClosableResource) {
                    ((com.justsyncit.storage.ClosableResource) resource).close();
                } else if (resource instanceof java.io.Closeable) {
                    ((java.io.Closeable) resource).close();
                } else if (resource instanceof java.lang.AutoCloseable) {
                    ((java.lang.AutoCloseable) resource).close();
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Failed to close resource: " + e.getMessage(), e);
            }
        }
    }

    // Network simulation helpers
    protected void enablePoorNetworkSimulation() {
        if (networkServiceWithSimulation instanceof NetworkSimulationUtil.NetworkConditionSimulator) {
            NetworkSimulationUtil.NetworkConditionSimulator simulator = (NetworkSimulationUtil.NetworkConditionSimulator) networkServiceWithSimulation;
            simulator.enableLatencySimulation(500);
            simulator.enablePacketLossSimulation(15);
            simulator.enableConnectionFailureSimulation(10);
        }
    }

    protected void disableNetworkSimulation() {
        if (networkServiceWithSimulation instanceof NetworkSimulationUtil.NetworkConditionSimulator) {
            NetworkSimulationUtil.NetworkConditionSimulator simulator = (NetworkSimulationUtil.NetworkConditionSimulator) networkServiceWithSimulation;
            simulator.disableLatencySimulation();
            simulator.disablePacketLossSimulation();
            simulator.disableConnectionFailureSimulation();
        }
    }

    // Cross-protocol testing helpers
    protected void testWithBothTransports(TransportTestOperation operation) throws Exception {
        // Test with TCP
        operation.run(TransportType.TCP);

        // Test with QUIC
        operation.run(TransportType.QUIC);
    }

    @FunctionalInterface
    protected interface TransportTestOperation {
        void run(TransportType transportType) throws Exception;
    }
}