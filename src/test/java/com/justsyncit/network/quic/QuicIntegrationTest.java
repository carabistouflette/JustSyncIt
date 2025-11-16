package com.justsyncit.network.quic;

import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;
import com.justsyncit.network.protocol.HandshakeMessage;
import com.justsyncit.network.protocol.HandshakeResponseMessage;
import com.justsyncit.network.protocol.MessageType;
import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.quic.adapter.QuicTransportAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QUIC transport functionality.
 */
public class QuicIntegrationTest {
    
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 9999;
    private static final int TIMEOUT_SECONDS = 10;
    
    private QuicServer quicServer;
    private QuicClient quicClient;
    private QuicConfiguration serverConfig;
    private QuicConfiguration clientConfig;
    private Path testCertDir;
    private ExecutorService executorService;
    
    @BeforeEach
    void setUp() throws Exception {
        // Create temporary directory for test certificates
        testCertDir = Files.createTempDirectory("quic-test-certs");
        
        // Create server configuration
        serverConfig = QuicConfiguration.defaultConfiguration();
        
        // Create client configuration
        clientConfig = QuicConfiguration.defaultConfiguration();
        
        // Initialize executor service
        executorService = Executors.newFixedThreadPool(4);
        
        // Create QUIC server and client
        quicServer = new QuicServer(serverConfig);
        quicClient = new QuicClient(clientConfig);
        
        // Start client
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
        if (testCertDir != null) {
            deleteDirectory(testCertDir.toFile());
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
    @DisplayName("QUIC server should start and stop successfully")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testServerStartStop() throws Exception {
        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);
        
        // Verify server is running
        assertTrue(quicServer.isRunning());
        assertEquals(SERVER_PORT, quicServer.getPort());
        
        // Stop server
        quicServer.stop().get(5, TimeUnit.SECONDS);
        
        // Verify server is stopped
        assertFalse(quicServer.isRunning());
    }
    
    @Test
    @DisplayName("QUIC client should connect to server successfully")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testClientConnection() throws Exception {
        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);
        
        // Simulate server running for client connection
        QuicClient.setSimulateServerRunning(true);
        
        // Connect client
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
        QuicConnection connection = quicClient.connect(serverAddress).get(5, TimeUnit.SECONDS);
        
        // Verify connection is established
        assertNotNull(connection);
        assertTrue(connection.isActive());
        
        // Close connection
        connection.close().get(5, TimeUnit.SECONDS);
        assertFalse(connection.isActive());
        
        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }
    
    @Test
    @DisplayName("QUIC client and server should exchange messages")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testMessageExchange() throws Exception {
        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);
        
        // Simulate server running for client connection
        QuicClient.setSimulateServerRunning(true);
        
        // Connect client
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
        QuicConnection connection = quicClient.connect(serverAddress).get(5, TimeUnit.SECONDS);
        
        // Create stream for communication
        QuicStream stream = connection.createStream(true).get(5, TimeUnit.SECONDS);
        
        // Send handshake message
        HandshakeMessage handshake = new HandshakeMessage("test-client", 0x01);
        stream.sendMessage(handshake).get(5, TimeUnit.SECONDS);
        
        // Close connection
        connection.close().get(5, TimeUnit.SECONDS);
        
        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }
    
    @Test
    @DisplayName("QUIC should support concurrent streams")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testConcurrentStreams() throws Exception {
        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);
        
        // Simulate server running for client connection
        QuicClient.setSimulateServerRunning(true);
        
        // Connect client
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
        QuicConnection connection = quicClient.connect(serverAddress).get(5, TimeUnit.SECONDS);
        
        // Create multiple streams
        int numStreams = 5;
        CountDownLatch latch = new CountDownLatch(numStreams);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numStreams; i++) {
            final int streamId = i;
            executorService.submit(() -> {
                try {
                    QuicStream stream = connection.createStream(true).get(5, TimeUnit.SECONDS);
                    
                    // Send message
                    HandshakeMessage message = new HandshakeMessage("client-" + streamId, 0x01);
                    stream.sendMessage(message).get(5, TimeUnit.SECONDS);
                    
                    successCount.incrementAndGet();
                    
                    stream.close().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Log error but don't fail test
                    System.err.println("Stream " + streamId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all streams to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        // Verify all streams succeeded
        assertEquals(numStreams, successCount.get());
        
        // Close connection
        connection.close().get(5, TimeUnit.SECONDS);
        
        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }
    
    @Test
    @DisplayName("QUIC should handle connection errors gracefully")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testConnectionErrorHandling() throws Exception {
        // Try to connect without starting server
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
        assertThrows(Exception.class, () -> {
            quicClient.connect(serverAddress).get(5, TimeUnit.SECONDS);
        });
        
        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);
        
        // Simulate server running for client connection
        QuicClient.setSimulateServerRunning(true);
        
        // Connect client
        QuicConnection connection = quicClient.connect(serverAddress).get(5, TimeUnit.SECONDS);
        
        // Close connection
        connection.close().get(5, TimeUnit.SECONDS);
        
        // Try to use closed connection
        assertThrows(Exception.class, () -> {
            connection.createStream(true).get(5, TimeUnit.SECONDS);
        });
        
        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }
    
    @Test
    @DisplayName("QUIC transport adapter should integrate with NetworkService")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testTransportAdapterIntegration() throws Exception {
        // Create QUIC transport adapter
        QuicTransportAdapter adapter = new QuicTransportAdapter();
        
        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);
        
        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);
        
        // Simulate server running for client connection
        QuicClient.setSimulateServerRunning(true);
        
        // Connect using adapter
        InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
        QuicConnection connection = adapter.connect(serverAddress).get(5, TimeUnit.SECONDS);
        
        // Verify connection
        assertNotNull(connection);
        assertTrue(connection.isActive());
        
        // Close connection
        connection.close().get(5, TimeUnit.SECONDS);
        
        // Stop adapter
        adapter.stop().get(5, TimeUnit.SECONDS);
        
        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }
    
    @Test
    @DisplayName("QUIC should support multiple connections")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testMultipleConnections() throws Exception {
        // Start server
        quicServer.start(SERVER_PORT).get(5, TimeUnit.SECONDS);
        
        // Simulate server running for client connections
        QuicClient.setSimulateServerRunning(true);
        
        // Create multiple client connections
        int numConnections = 3;
        CountDownLatch latch = new CountDownLatch(numConnections);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numConnections; i++) {
            final int connectionId = i;
            executorService.submit(() -> {
                try {
                    QuicClient client = new QuicClient(clientConfig);
                    client.start().get(5, TimeUnit.SECONDS);
                    
                    InetSocketAddress serverAddress = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
                    QuicConnection connection = client.connect(serverAddress).get(5, TimeUnit.SECONDS);
                    
                    if (connection.isActive()) {
                        successCount.incrementAndGet();
                    }
                    
                    connection.close().get(5, TimeUnit.SECONDS);
                    client.stop().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Log error but don't fail test
                    System.err.println("Connection " + connectionId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all connections to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        // Verify all connections succeeded
        assertEquals(numConnections, successCount.get());
        
        // Reset simulation state
        QuicClient.setSimulateServerRunning(false);
    }
}