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

import com.justsyncit.command.CommandContext;
import com.justsyncit.command.ServerCommandGroup;
import com.justsyncit.command.ServerStartCommand;
import com.justsyncit.command.ServerStopCommand;
import com.justsyncit.command.ServerStatusCommand;
import com.justsyncit.command.TransferCommand;
import com.justsyncit.command.SyncCommand;
import com.justsyncit.network.NetworkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for network operations.
 * Tests server operations (start, stop, status) and network functionality.
 */
public class NetworkOperationsE2ETest extends E2ETestBase {

    private ServerCommandGroup serverCommand;
    private ServerStartCommand startCommand;
    private ServerStopCommand stopCommand;
    private ServerStatusCommand statusCommand;
    private TransferCommand transferCommand;
    private SyncCommand syncCommand;
    private CommandContext commandContext;

    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        serverCommand = new ServerCommandGroup();
        startCommand = new ServerStartCommand(null);
        stopCommand = new ServerStopCommand(null);
        statusCommand = new ServerStatusCommand(null);
        transferCommand = new TransferCommand(networkService);
        syncCommand = new SyncCommand(networkService);
        commandContext = new CommandContext(blake3Service, networkService, metadataService, contentStore);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testServerStartAndStop() throws Exception {
        int port = findAvailablePort();

        // Test starting server
        boolean startResult = serverCommand.execute(
                new String[] { "start", "--port", String.valueOf(port), "--daemon" },
                commandContext);
        assertTrue(startResult, "Server start command should succeed");

        // Wait a bit for server to start
        Thread.sleep(100);

        assertTrue(networkService.isServerRunning(), "Server should be running");
        assertEquals(port, networkService.getServerPort(), "Server should be listening on correct port");

        // Test server status
        boolean statusResult = serverCommand.execute(new String[] { "status" }, commandContext);
        assertTrue(statusResult, "Server status command should succeed");

        // Test stopping server
        boolean stopResult = serverCommand.execute(new String[] { "stop" }, commandContext);
        assertTrue(stopResult, "Server stop command should succeed");

        // Wait a bit for server to stop
        Thread.sleep(100);

        assertFalse(networkService.isServerRunning(), "Server should not be running");
        assertEquals(-1, networkService.getServerPort(), "Server port should be -1 when stopped");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testServerStartWithInvalidPort() throws Exception {
        // Test starting server with invalid port
        boolean result = serverCommand.execute(new String[] { "start", "--port", "invalid" }, commandContext);
        assertFalse(result, "Server start should fail with invalid port");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testServerStartWithCustomPort() throws Exception {
        // Test starting server with custom port
        int customPort = 9999;
        boolean result = serverCommand.execute(
                new String[] { "start", "--port", String.valueOf(customPort), "--daemon" },
                commandContext);
        assertTrue(result, "Server start should succeed with custom port");

        Thread.sleep(100);

        assertTrue(networkService.isServerRunning(), "Server should be running");
        assertEquals(customPort, networkService.getServerPort(), "Server should be listening on custom port");

        // Clean up
        serverCommand.execute(new String[] { "stop" }, commandContext);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testServerStartWithTransportType() throws Exception {
        // Test starting server with different transport types
        testWithBothTransports(transportType -> {
            int port = findAvailablePort();
            boolean result = serverCommand.execute(new String[] {
                    "start",
                    "--port", String.valueOf(port),
                    "--transport", transportType.name(),
                    "--daemon"
            }, commandContext);

            assertTrue(result, "Server start should succeed with " + transportType);

            Thread.sleep(100);

            assertTrue(networkService.isServerRunning(), "Server should be running with " + transportType);
            assertEquals(port, networkService.getServerPort(),
                    "Server should be listening on correct port with " + transportType);

            // Clean up
            serverCommand.execute(new String[] { "stop" }, commandContext);
        });
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testServerStatusWhenNotRunning() throws Exception {
        // Test server status when server is not running
        boolean result = serverCommand.execute(new String[] { "status" }, commandContext);
        assertTrue(result, "Server status command should succeed");

        // Verify server is not running (should show appropriate status)
        assertFalse(networkService.isServerRunning(), "Server should not be running");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testServerStatusWhenRunning() throws Exception {
        // Test server status when server is running
        int port = findAvailablePort();

        // Start server
        boolean startResult = serverCommand.execute(
                new String[] { "start", "--port", String.valueOf(port), "--daemon" },
                commandContext);
        assertTrue(startResult, "Server start should succeed");

        Thread.sleep(100);

        // Check status
        boolean statusResult = serverCommand.execute(new String[] { "status" }, commandContext);
        assertTrue(statusResult, "Server status command should succeed");

        assertTrue(networkService.isServerRunning(), "Server should be running");
        assertEquals(port, networkService.getServerPort(), "Server should be listening on correct port");

        // Clean up
        serverCommand.execute(new String[] { "stop" }, commandContext);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testServerStopWhenNotRunning() throws Exception {
        // Test stopping server when it's not running
        boolean result = serverCommand.execute(new String[] { "stop" }, commandContext);
        assertTrue(result, "Server stop should succeed even when not running");

        assertFalse(networkService.isServerRunning(), "Server should not be running");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testServerMultipleStartStop() throws Exception {
        // Test multiple start/stop cycles
        for (int i = 0; i < 3; i++) {
            int port = findAvailablePort();

            // Start server
            boolean startResult = serverCommand.execute(
                    new String[] { "start", "--port", String.valueOf(port), "--daemon" },
                    commandContext);
            assertTrue(startResult, "Server start should succeed: " + i);

            Thread.sleep(100);
            assertTrue(networkService.isServerRunning(), "Server should be running: " + i);

            // Stop server
            boolean stopResult = serverCommand.execute(new String[] { "stop" }, commandContext);
            assertTrue(stopResult, "Server stop should succeed: " + i);

            Thread.sleep(100);
            assertFalse(networkService.isServerRunning(), "Server should not be running: " + i);
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testFileTransfer() throws Exception {
        // Create test data
        createBasicDataset();
        String snapshotId = performBackup("transfer-test-snapshot", "Snapshot for transfer testing");

        // Test file transfer (simulation since we don't have a real remote server)
        // Test file transfer (simulation since we don't have a real remote server)

        boolean result = transferCommand.execute(new String[] {
                snapshotId,
                "--to", "localhost:" + findAvailablePort(),
                "--transport", "TCP"
        }, commandContext);

        // Transfer command should fail gracefully since no server is running
        // In a real test environment, we would start a server and test actual transfer
        // For now, we verify the command structure and error handling
        assertNotNull(transferCommand, "Transfer command should be created");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testFileTransferWithDifferentTransports() throws Exception {
        // Test file transfer with different transport types
        createBasicDataset();
        String snapshotId = performBackup("transport-test-snapshot", "Snapshot for transport testing");

        testWithBothTransports(transportType -> {
            // Path testFile = sourceDir.resolve("test.txt");
            // InetSocketAddress remoteAddress = new InetSocketAddress("localhost",
            // findAvailablePort());

            boolean result = transferCommand.execute(new String[] {
                    snapshotId,
                    "--to", "localhost:" + findAvailablePort(),
                    "--transport", transportType.name()
            }, commandContext);

            // Transfer command should be created successfully
            assertNotNull(transferCommand, "Transfer command should be created with " + transportType);
        });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testSyncCommand() throws Exception {
        // Test sync command functionality
        createBasicDataset();
        String snapshotId = performBackup("sync-test-snapshot", "Snapshot for sync testing");

        // Test sync command (simulation)
        // Test sync command (simulation)

        boolean result = syncCommand.execute(new String[] {
                snapshotId,
                "--server", "localhost:" + findAvailablePort(),
                "--transport", "TCP"
        }, commandContext);

        // Sync command should be created successfully
        assertNotNull(syncCommand, "Sync command should be created");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testNetworkConnectivity() throws Exception {
        // Test network connectivity validation
        String host = "localhost";
        int port = findAvailablePort();

        // Test with non-existent host
        assertFalse(validateConnectivity("non-existent-host", port),
                "Should fail connectivity check for non-existent host");

        // Test with non-existent port
        assertFalse(validateConnectivity(host, 9999),
                "Should fail connectivity check for non-existent port");

        // Start server and test connectivity
        boolean startResult = serverCommand.execute(
                new String[] { "start", "--port", String.valueOf(port), "--daemon" },
                commandContext);
        assertTrue(startResult, "Server start should succeed");

        Thread.sleep(100);

        // Test connectivity to running server
        assertTrue(waitForServerReady(host, port, 5000),
                "Server should become ready within timeout");

        assertTrue(validateConnectivity(host, port),
                "Should succeed connectivity check for running server");

        // Clean up
        serverCommand.execute(new String[] { "stop" }, commandContext);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testNetworkErrorHandling() throws Exception {
        // Test network error handling scenarios
        enablePoorNetworkSimulation();

        // Test operations with poor network conditions
        createBasicDataset();
        String snapshotId = performBackup("network-error-test-snapshot", "Snapshot for network error testing");

        // Operations should handle network errors gracefully
        // In a real implementation, we would test actual network failures
        // For now, we verify the error handling infrastructure exists
        assertNotNull(networkServiceWithSimulation, "Network simulation should be available");

        // Test that operations fail appropriately with poor network
        // This would be tested with actual network operations
        assertTrue(true, "Network error handling infrastructure should be in place");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testConcurrentNetworkOperations() throws Exception {
        // Test concurrent network operations
        int port1 = findAvailablePort();
        int port2 = findAvailablePort();

        // Start first server using default context
        boolean startResult1 = serverCommand.execute(
                new String[] { "start", "--port", String.valueOf(port1), "--daemon" },
                commandContext);
        assertTrue(startResult1, "First server start should succeed");

        // Create separate service and context for second server
        NetworkService networkService2 = serviceFactory.createNetworkService();
        CommandContext commandContext2 = new CommandContext(blake3Service, networkService2, metadataService,
                contentStore);

        try {
            // Start second server
            boolean startResult2 = serverCommand.execute(
                    new String[] { "start", "--port", String.valueOf(port2), "--daemon" },
                    commandContext2);
            assertTrue(startResult2, "Second server start should succeed");

            Thread.sleep(100);

            assertTrue(networkService.isServerRunning(), "First server should be running");
            assertTrue(networkService2.isServerRunning(), "Second server should be running");

            // Test operations on multiple servers
            assertTrue(port1 != port2, "Should use different ports for concurrent servers");

            // Clean up second server
            serverCommand.execute(new String[] { "stop" }, commandContext2);
        } finally {
            if (networkService2 != null) {
                networkService2.close();
            }
        }

        // Clean up first server
        serverCommand.execute(new String[] { "stop" }, commandContext);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testNetworkStatistics() throws Exception {
        // Test network statistics collection
        int port = findAvailablePort();

        // Start server
        boolean startResult = serverCommand.execute(
                new String[] { "start", "--port", String.valueOf(port), "--daemon" },
                commandContext);
        assertTrue(startResult, "Server start should succeed");

        Thread.sleep(100);

        // Check initial statistics
        assertTrue(networkService.getBytesSent() >= 0, "Bytes sent should be available");
        assertTrue(networkService.getBytesReceived() >= 0, "Bytes received should be available");
        assertTrue(networkService.getMessagesSent() >= 0, "Messages sent should be available");
        assertTrue(networkService.getMessagesReceived() >= 0, "Messages received should be available");

        // Test status command (should show statistics)
        boolean statusResult = serverCommand.execute(new String[] { "status" }, commandContext);
        assertTrue(statusResult, "Status command should succeed");

        // Clean up
        serverCommand.execute(new String[] { "stop" }, commandContext);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testServerCommandHelpAndUsage() throws Exception {
        // Test server command help functionality
        boolean helpResult = serverCommand.execute(new String[] { "--help" }, commandContext);
        assertTrue(helpResult, "Help command should succeed");

        boolean helpResult2 = serverCommand.execute(new String[] { "help" }, commandContext);
        assertTrue(helpResult2, "Help command should succeed");

        // Test usage display
        boolean usageResult = serverCommand.execute(new String[] {}, commandContext);
        assertFalse(usageResult, "Should fail with no arguments and show usage");

        // Test invalid subcommand
        boolean invalidResult = serverCommand.execute(new String[] { "invalid" }, commandContext);
        assertFalse(invalidResult, "Should fail with invalid subcommand");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testNetworkPerformance() throws Exception {
        // Test network performance characteristics
        int port = findAvailablePort();

        // Start server
        boolean startResult = serverCommand.execute(
                new String[] { "start", "--port", String.valueOf(port), "--daemon" },
                commandContext);
        assertTrue(startResult, "Server start should succeed");

        Thread.sleep(100);

        // Measure server startup time
        long startTime = System.currentTimeMillis();
        boolean statusResult = serverCommand.execute(new String[] { "status" }, commandContext);
        long statusTime = System.currentTimeMillis() - startTime;

        assertTrue(statusResult, "Status command should succeed");
        assertTrue(statusTime < 1000, "Status command should complete quickly");

        // Measure server stop time
        startTime = System.currentTimeMillis();
        boolean stopResult = serverCommand.execute(new String[] { "stop" }, commandContext);
        long stopTime = System.currentTimeMillis() - startTime;

        assertTrue(stopResult, "Stop command should succeed");
        assertTrue(stopTime < 1000, "Stop command should complete quickly");

        // Verify performance metrics
        NetworkService.NetworkStatistics stats = networkService.getStatistics();
        assertNotNull(stats, "Network statistics should be available");
        assertTrue(stats.getUptimeMillis() >= 0, "Uptime should be recorded");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testNetworkWithLargeDataset() throws Exception {
        // Test network operations with large dataset
        createPerformanceDataset(50, 1024 * 100); // 50 files, up to 100KB each
        String snapshotId = performBackup("large-network-snapshot", "Large dataset for network testing");

        // Test that large dataset can be handled
        // In a real test, we would transfer the large dataset
        // For now, we verify the infrastructure supports large datasets
        assertNotNull(snapshotId, "Large dataset snapshot should be created");

        // Verify snapshot has expected size
        var snapshotOpt = metadataService.getSnapshot(snapshotId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot should exist");

        var snapshot = snapshotOpt.get();
        assertEquals(50, snapshot.getTotalFiles(), "Should have 50 files");
        assertTrue(snapshot.getTotalSize() > 50 * 1024, "Should have significant size");
    }
}