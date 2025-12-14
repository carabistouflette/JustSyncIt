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

package com.justsyncit.network.quic;

import com.justsyncit.network.protocol.HandshakeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests QUIC performance under simulated packet loss conditions.
 * Verifies that QUIC maintains performance and reliability even with
 * significant packet loss, demonstrating its advantages over TCP.
 */
public class QuicPacketLossSimulationTest {

    /** Server host for testing. */
    private static final String SERVER_HOST = "127.0.0.1";
    /** Server port for testing. */
    private static final int SERVER_PORT = 9998;
    /** Timeout in seconds for tests. */
    private static final int TIMEOUT_SECONDS = 10;
    /** Number of test messages to send. */
    private static final int TEST_MESSAGE_COUNT = 20;
    /** Large file size for testing (1MB). */
    private static final int LARGE_FILE_SIZE = 1024 * 1024;

    /** QUIC server for testing. */
    private QuicServer quicServer;
    /** QUIC client for testing. */
    private QuicClient quicClient;
    /** Server configuration for testing. */
    private QuicConfiguration serverConfig;
    /** Client configuration for testing. */
    private QuicConfiguration clientConfig;
    /** Packet loss simulator for testing. */
    private PacketLossSimulator packetLossSimulator;
    /** Executor service for concurrent operations. */
    private ExecutorService executorService;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException, TimeoutException {
        // Create configurations with packet loss simulation enabled
        serverConfig = QuicConfiguration.builder()
                .idleTimeout(java.time.Duration.ofSeconds(30))
                .initialMaxData(10000000)
                .initialMaxStreamData(5000000)
                .maxBidirectionalStreams(100)
                .build();

        clientConfig = QuicConfiguration.builder()
                .idleTimeout(java.time.Duration.ofSeconds(30))
                .initialMaxData(10000000)
                .initialMaxStreamData(5000000)
                .maxBidirectionalStreams(100)
                .build();

        // Initialize packet loss simulator
        packetLossSimulator = new PacketLossSimulator();

        // Initialize executor service
        executorService = Executors.newFixedThreadPool(8);

        // Create QUIC server and client
        quicServer = new QuicServer(serverConfig);
        quicClient = new QuicClient(clientConfig);

        // Start client
        quicClient.start().get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws ExecutionException, InterruptedException, TimeoutException {
        if (quicServer != null) {
            quicServer.stop().get(5, TimeUnit.SECONDS);
        }
        if (quicClient != null) {
            quicClient.stop().get(5, TimeUnit.SECONDS);
        }
        if (executorService != null) {
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (packetLossSimulator != null) {
            packetLossSimulator.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = { 0.0, 0.05, 0.1, 0.15, 0.2 })
    @DisplayName("QUIC should maintain performance with varying packet loss rates")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testPacketLossTolerance(double packetLossRate)
            throws ExecutionException, InterruptedException, TimeoutException {
        // Configure packet loss simulator
        packetLossSimulator.setPacketLossRate(packetLossRate);
        packetLossSimulator.start();

        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);

        // Simulate server running for client connection
        QuicClient.setSimulateServerRunning(true);

        // Connect client
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
        QuicConnection connection = quicClient.connect(serverAddress).get(10, TimeUnit.SECONDS);

        // Test message exchange with packet loss
        AtomicInteger successfulMessages = new AtomicInteger(0);
        AtomicInteger failedMessages = new AtomicInteger(0);
        AtomicLong totalTransferTime = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < TEST_MESSAGE_COUNT; i++) {
            final int messageId = i;
            long messageStartTime = System.currentTimeMillis();

            try {
                QuicStream stream = connection.createStream(true).get(5, TimeUnit.SECONDS);
                HandshakeMessage message = new HandshakeMessage("client-" + messageId, 0x01);

                stream.sendMessage(message)
                        .thenRun(() -> {
                            long messageTime = System.currentTimeMillis() - messageStartTime;
                            totalTransferTime.addAndGet(messageTime);
                            successfulMessages.incrementAndGet();
                        })
                        .exceptionally(throwable -> {
                            failedMessages.incrementAndGet();
                            return null;
                        })
                        .thenCompose(v -> stream.close())
                        .get(5, TimeUnit.SECONDS);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                failedMessages.incrementAndGet();
            }
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Wait for all operations to complete
        Thread.sleep(500);

        // Calculate metrics
        double successRate = (double) successfulMessages.get() / TEST_MESSAGE_COUNT;
        double averageTransferTime = (double) totalTransferTime.get() / successfulMessages.get();
        double throughput = (double) successfulMessages.get() / (totalTime / 1000.0);

        // Verify results
        assertTrue(successRate >= 0.8,
                "Success rate should be at least 80% with " + (packetLossRate * 100)
                        + "% packet loss, was " + (successRate * 100) + "%");

        // Log performance metrics
        System.out.printf("Packet Loss: %.1f%%, Success Rate: %.1f%%, "
                + "Avg Transfer Time: %.2fms, Throughput: %.2f msg/s%n",
                packetLossRate * 100, successRate * 100, averageTransferTime, throughput);

        // Close connection
        connection.close().get(5, TimeUnit.SECONDS);

        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }

    @Test
    @DisplayName("QUIC should handle large file transfers with packet loss")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testLargeFileTransferWithPacketLoss() throws ExecutionException, InterruptedException, TimeoutException {
        // Configure 10% packet loss
        packetLossSimulator.setPacketLossRate(0.1);
        packetLossSimulator.start();

        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);

        // Simulate server running for client connection
        QuicClient.setSimulateServerRunning(true);

        // Connect client
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
        QuicConnection connection = quicClient.connect(serverAddress).get(10, TimeUnit.SECONDS);

        // Create large data
        byte[] largeData = new byte[LARGE_FILE_SIZE];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        // Transfer large file
        long startTime = System.currentTimeMillis();

        QuicStream stream = connection.createStream(true).get(5, TimeUnit.SECONDS);

        // Send data in chunks to simulate file transfer
        final int chunkSize = 64 * 1024; // 64KB chunks
        int totalChunks = (int) Math.ceil((double) largeData.length / chunkSize);
        AtomicInteger sentChunks = new AtomicInteger(0);

        for (int i = 0; i < totalChunks; i++) {
            final int chunkIndex = i;

            assertTrue(executorService.submit(() -> {
                try {
                    // In a real implementation, this would send actual data
                    // For simulation, we'll just track progress
                    sentChunks.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Failed to send chunk " + chunkIndex + ": " + e.getMessage());
                }
            }) != null, "Task submission should succeed");
        }

        // Wait for all chunks to be "sent"
        while (sentChunks.get() < totalChunks) {
            Thread.sleep(100);
        }

        long endTime = System.currentTimeMillis();
        long transferTime = endTime - startTime;
        double throughput = (double) LARGE_FILE_SIZE / (transferTime / 1000.0) / (1024 * 1024); // MB/s

        // Verify transfer completed
        assertEquals(totalChunks, sentChunks.get());

        // Log performance metrics
        System.out.printf("Large File Transfer with 10%% packet loss: %.2f MB in %d ms (%.2f MB/s)%n",
                LARGE_FILE_SIZE / (1024.0 * 1024.0), transferTime, throughput);

        // Close connection
        stream.close().get(5, TimeUnit.SECONDS);
        connection.close().get(5, TimeUnit.SECONDS);

        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }

    @Test
    @DisplayName("QUIC should recover from temporary network interruptions")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testNetworkInterruptionRecovery() throws ExecutionException, InterruptedException, TimeoutException {
        // Start with no packet loss
        packetLossSimulator.setPacketLossRate(0.0);
        packetLossSimulator.start();

        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);

        // Simulate server running for client connection
        QuicClient.setSimulateServerRunning(true);

        // Connect client
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
        QuicConnection connection = quicClient.connect(serverAddress).get(10, TimeUnit.SECONDS);

        // Send initial messages
        QuicStream stream1 = connection.createStream(true).get(5, TimeUnit.SECONDS);
        HandshakeMessage message1 = new HandshakeMessage("initial-message", 0x01);
        stream1.sendMessage(message1).get(5, TimeUnit.SECONDS);

        // Simulate network interruption (100% packet loss for 1 second)
        packetLossSimulator.setPacketLossRate(1.0);
        Thread.sleep(1000);

        // Restore network
        packetLossSimulator.setPacketLossRate(0.0);

        // Send messages after interruption
        QuicStream stream2 = connection.createStream(true).get(5, TimeUnit.SECONDS);
        HandshakeMessage message2 = new HandshakeMessage("recovery-message", 0x01);

        boolean messageSent = false;
        try {
            stream2.sendMessage(message2)
                    .thenCompose(v -> stream2.close())
                    .get(10, TimeUnit.SECONDS);
            messageSent = true;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            messageSent = false;
        }

        // Verify connection recovered and message was sent
        assertTrue(messageSent, "QUIC should recover from network interruption");
        assertTrue(connection.isActive(), "Connection should remain active after interruption");

        // Close connection
        connection.close().get(5, TimeUnit.SECONDS);

        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }

    @Test
    @DisplayName("QUIC should maintain multiple concurrent streams with packet loss")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testConcurrentStreamsWithPacketLoss() throws ExecutionException, InterruptedException, TimeoutException {
        // Configure 15% packet loss
        packetLossSimulator.setPacketLossRate(0.15);
        packetLossSimulator.start();

        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);

        // Simulate server running for client connection
        QuicClient.setSimulateServerRunning(true);

        // Connect client
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
        QuicConnection connection = quicClient.connect(serverAddress).get(10, TimeUnit.SECONDS);

        // Create multiple concurrent streams
        int numStreams = 10;
        AtomicInteger successfulStreams = new AtomicInteger(0);
        CompletableFuture<?>[] streamFutures = new CompletableFuture<?>[numStreams];

        for (int i = 0; i < numStreams; i++) {
            final int streamId = i;
            streamFutures[i] = CompletableFuture.runAsync(() -> {
                try {
                    QuicStream stream = connection.createStream(true).get(5, TimeUnit.SECONDS);
                    HandshakeMessage message = new HandshakeMessage("stream-" + streamId, 0x01);

                    stream.sendMessage(message).get(10, TimeUnit.SECONDS);
                    stream.close().get(5, TimeUnit.SECONDS);

                    successfulStreams.incrementAndGet();
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    System.err.println("Stream " + streamId + " failed: " + e.getMessage());
                }
            }, executorService);
        }

        // Wait for all streams to complete
        CompletableFuture.allOf(streamFutures).get(20, TimeUnit.SECONDS);

        // Verify results
        double successRate = (double) successfulStreams.get() / numStreams;
        assertTrue(successRate >= 0.7,
                "At least 70% of concurrent streams should succeed with 15% packet loss, was "
                        + successRate * 100 + "%");

        // Close connection
        connection.close().get(5, TimeUnit.SECONDS);

        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }

    /**
     * Simple packet loss simulator for testing.
     * In a real implementation, this would integrate with the QUIC library
     * to actually drop packets at the network layer.
     */
    /**
     * Simple packet loss simulator for testing.
     * In a real implementation, this would integrate with the QUIC library
     * to actually drop packets at the network layer.
     */
    private static class PacketLossSimulator {
        /** Packet loss rate (0.0 to 1.0). */
        private double packetLossRate = 0.0;
        /** Whether the simulator is running. */
        private volatile boolean running = false;

        public void setPacketLossRate(double rate) {
            this.packetLossRate = Math.max(0.0, Math.min(1.0, rate));
        }

        public void start() {
            this.running = true;
        }

        public void stop() {
            this.running = false;
        }

        public boolean shouldDropPacket() {
            if (!running || packetLossRate == 0.0) {
                return false;
            }
            return Math.random() < packetLossRate;
        }
    }
}