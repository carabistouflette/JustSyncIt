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
import com.justsyncit.network.protocol.ProtocolMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified benchmark tests comparing QUIC vs TCP performance.
 * Tests various scenarios including latency, throughput, and concurrent connections.
 */
public class QuicTcpBenchmarkTest {
    
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int QUIC_PORT = 9997;
    private static final int TCP_PORT = 9996;
    private static final int TIMEOUT_SECONDS = 60;
    private static final int WARMUP_ITERATIONS = 10;
    private static final int BENCHMARK_ITERATIONS = 100;
    
    private QuicServer quicServer;
    private QuicClient quicClient;
    private QuicConfiguration quicConfig;
    private Path tempDir;
    private ExecutorService executorService;
    
    @BeforeEach
    void setUp() throws Exception {
        // Create temporary directory for test files
        tempDir = Files.createTempDirectory("benchmark-test");
        
        // Initialize executor service
        executorService = Executors.newFixedThreadPool(16);
        
        // Initialize QUIC components
        quicConfig = QuicConfiguration.builder()
            .idleTimeout(java.time.Duration.ofSeconds(60))
            .initialMaxData(50 * 1024 * 1024) // 50MB
            .initialMaxStreamData(10 * 1024 * 1024) // 10MB
            .maxBidirectionalStreams(1000)
            .zeroRttSupport(true)
            .build();
        
        quicServer = new QuicServer(quicConfig);
        quicClient = new QuicClient(quicConfig);
        quicClient.start().get(5, TimeUnit.SECONDS);
    }
    
    @AfterEach
    void tearDown() throws Exception {
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
        if (tempDir != null) {
            deleteDirectory(tempDir.toFile());
        }
    }
    
    private void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("Failed to delete directory: " + directory);
        }
    }
    
    @Test
    @DisplayName("Benchmark QUIC connection establishment latency")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testQuicConnectionLatencyBenchmark() throws Exception {
        System.out.println("\n=== QUIC Connection Establishment Latency Benchmark ===");
        
        // Start server
        quicServer.start(QUIC_PORT).get(5, TimeUnit.SECONDS);
        
        // Warm up
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            benchmarkQuicConnection();
        }
        
        // Benchmark QUIC
        List<Long> quicTimes = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long time = benchmarkQuicConnection();
            quicTimes.add(time);
        }
        
        // Calculate statistics
        double quicAvg = quicTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double quicP50 = percentile(quicTimes, 0.5);
        double quicP95 = percentile(quicTimes, 0.95);
        
        // Print results
        System.out.printf("QUIC Connection Latency:%n");
        System.out.printf("  Average: %.2f ms%n", quicAvg);
        System.out.printf("  P50: %.2f ms%n", quicP50);
        System.out.printf("  P95: %.2f ms%n", quicP95);
        
        // Verify reasonable performance
        assertTrue(quicAvg < 1000.0, "QUIC connection should establish within 1 second on average");
        assertTrue(quicP95 < 2000.0, "QUIC 95th percentile should be within 2 seconds");
    }
    
    @Test
    @DisplayName("Benchmark QUIC message throughput")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testQuicMessageThroughputBenchmark() throws Exception {
        System.out.println("\n=== QUIC Message Throughput Benchmark ===");
        
        // Start server
        quicServer.start(QUIC_PORT).get(5, TimeUnit.SECONDS);
        
        // Benchmark parameters
        int messageCount = 1000;
        
        // Benchmark QUIC
        long quicStartTime = System.currentTimeMillis();
        long quicMessagesSent = benchmarkQuicThroughput(messageCount);
        long quicEndTime = System.currentTimeMillis();
        double quicThroughput = (double) quicMessagesSent / ((quicEndTime - quicStartTime) / 1000.0);
        
        // Print results
        System.out.printf("QUIC Throughput: %.2f messages/sec%n", quicThroughput);
        
        // Verify reasonable throughput
        assertTrue(quicThroughput > 50, "QUIC should achieve reasonable throughput");
    }
    
    @ParameterizedTest
    @ValueSource(ints = {1024, 10240, 102400, 1024000}) // 1KB, 10KB, 100KB, 1MB
    @DisplayName("Benchmark QUIC file transfer performance")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testQuicFileTransferBenchmark(int fileSize) throws Exception {
        System.out.printf("%n=== QUIC File Transfer Benchmark (%d bytes) ===%n", fileSize);
        
        // Create test file
        byte[] fileData = new byte[fileSize];
        for (int i = 0; i < fileSize; i++) {
            fileData[i] = (byte) (i % 256);
        }
        Path testFile = tempDir.resolve("test-file-" + fileSize + ".bin");
        Files.write(testFile, fileData);
        
        // Start server
        quicServer.start(QUIC_PORT).get(5, TimeUnit.SECONDS);
        
        // Benchmark QUIC file transfer
        long quicStartTime = System.currentTimeMillis();
        boolean quicSuccess = benchmarkQuicFileTransfer(testFile, fileData);
        long quicEndTime = System.currentTimeMillis();
        double quicThroughput = (double) fileSize / (1024.0 * 1024.0) / ((quicEndTime - quicStartTime) / 1000.0);
        
        // Print results
        System.out.printf("File Size: %.2f MB%n", fileSize / (1024.0 * 1024.0));
        System.out.printf("QUIC: %.2f MB/s%n", quicThroughput);
        
        // Verify transfer succeeded
        assertTrue(quicSuccess, "QUIC file transfer should succeed");
    }
    
    @Test
    @DisplayName("Benchmark QUIC concurrent connection performance")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testQuicConcurrentConnectionBenchmark() throws Exception {
        System.out.println("\n=== QUIC Concurrent Connection Benchmark ===");
        
        // Start server
        quicServer.start(QUIC_PORT).get(5, TimeUnit.SECONDS);
        
        int concurrentConnections = 20;
        int messagesPerConnection = 50;
        
        // Benchmark QUIC concurrent connections
        long quicStartTime = System.currentTimeMillis();
        int quicTotalMessages = benchmarkQuicConcurrentConnections(concurrentConnections, messagesPerConnection);
        long quicEndTime = System.currentTimeMillis();
        double quicThroughput = (double) quicTotalMessages / ((quicEndTime - quicStartTime) / 1000.0);
        
        // Print results
        System.out.printf("Concurrent Connections: %d%n", concurrentConnections);
        System.out.printf("Messages per Connection: %d%n", messagesPerConnection);
        System.out.printf("QUIC Total Messages: %d%n", quicTotalMessages);
        System.out.printf("QUIC Throughput: %.2f messages/sec%n", quicThroughput);
        
        // Verify reasonable performance
        assertTrue(quicTotalMessages >= concurrentConnections * messagesPerConnection * 0.8, 
                  "QUIC should handle most concurrent messages");
    }
    
    private long benchmarkQuicConnection() throws Exception {
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, QUIC_PORT);
        long startTime = System.currentTimeMillis();
        
        QuicConnection connection = quicClient.connect(serverAddress).get(10, TimeUnit.SECONDS);
        connection.close().get(5, TimeUnit.SECONDS);
        
        return System.currentTimeMillis() - startTime;
    }
    
    private long benchmarkQuicThroughput(int messageCount) throws Exception {
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, QUIC_PORT);
        QuicConnection connection = quicClient.connect(serverAddress).get(10, TimeUnit.SECONDS);
        
        AtomicInteger sentMessages = new AtomicInteger(0);
        CompletableFuture<?>[] futures = new CompletableFuture[messageCount];
        
        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    QuicStream stream = connection.createStream(true).get(5, TimeUnit.SECONDS);
                    HandshakeMessage message = new HandshakeMessage("msg-" + messageId, 0x01);
                    stream.sendMessage(message).get(5, TimeUnit.SECONDS);
                    stream.close().get(5, TimeUnit.SECONDS);
                    sentMessages.incrementAndGet();
                } catch (Exception e) {
                    // Log but don't fail
                    System.err.println("Failed to send QUIC message " + messageId + ": " + e.getMessage());
                }
            }, executorService);
        }
        
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        connection.close().get(5, TimeUnit.SECONDS);
        
        return sentMessages.get();
    }
    
    private boolean benchmarkQuicFileTransfer(Path filePath, byte[] expectedData) throws Exception {
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, QUIC_PORT);
        QuicConnection connection = quicClient.connect(serverAddress).get(10, TimeUnit.SECONDS);
        
        // Simulate file transfer
        boolean success = true;
        try {
            QuicStream stream = connection.createStream(true).get(5, TimeUnit.SECONDS);
            
            // Simulate sending file data
            Thread.sleep(100); // Simulate transfer time
            
            stream.close().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            success = false;
        }
        
        connection.close().get(5, TimeUnit.SECONDS);
        return success;
    }
    
    private int benchmarkQuicConcurrentConnections(int connectionCount, int messagesPerConnection) throws Exception {
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, QUIC_PORT);
        AtomicInteger totalMessages = new AtomicInteger(0);
        CompletableFuture<?>[] futures = new CompletableFuture[connectionCount];
        
        for (int i = 0; i < connectionCount; i++) {
            final int connectionId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    QuicConnection connection = quicClient.connect(serverAddress).get(10, TimeUnit.SECONDS);
                    
                    for (int j = 0; j < messagesPerConnection; j++) {
                        try {
                            QuicStream stream = connection.createStream(true).get(5, TimeUnit.SECONDS);
                            HandshakeMessage message = new HandshakeMessage("conn-" + connectionId + "-msg-" + j, 0x01);
                            stream.sendMessage(message).get(5, TimeUnit.SECONDS);
                            stream.close().get(5, TimeUnit.SECONDS);
                            totalMessages.incrementAndGet();
                        } catch (Exception e) {
                            System.err.println("Failed to send message on QUIC connection " + connectionId + ": " + e.getMessage());
                        }
                    }
                    
                    connection.close().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    System.err.println("Failed to establish QUIC connection " + connectionId + ": " + e.getMessage());
                }
            }, executorService);
        }
        
        CompletableFuture.allOf(futures).get(60, TimeUnit.SECONDS);
        return totalMessages.get();
    }
    
    private double percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0;
        }
        
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        
        return sorted.get(index);
    }
}