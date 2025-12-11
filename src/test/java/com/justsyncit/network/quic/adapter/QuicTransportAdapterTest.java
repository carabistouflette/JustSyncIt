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

package com.justsyncit.network.quic.adapter;

import com.justsyncit.network.protocol.PingMessage;
import com.justsyncit.network.quic.QuicClient;
import com.justsyncit.network.quic.QuicConfiguration;
import com.justsyncit.network.quic.QuicConnection;
import com.justsyncit.network.quic.QuicStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for QuicTransportAdapter.
 */
@DisplayName("QuicTransportAdapter Tests")
public class QuicTransportAdapterTest {

    /** Mock QUIC client for testing. */
    @Mock
    private QuicClient mockQuicClient;

    /** QUIC transport adapter under test. */
    private QuicTransportAdapter adapter;
    /** QUIC configuration for testing. */
    private QuicConfiguration configuration;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        configuration = QuicConfiguration.defaultConfiguration();

        // Create adapter with mocked client
        adapter = new QuicTransportAdapter(configuration) {
            @Override
            protected QuicClient createQuicClient(QuicConfiguration config) {
                return mockQuicClient;
            }
        };

        // Mock the stop method to prevent NPE in tearDown
        when(mockQuicClient.stop()).thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void tearDown() throws ExecutionException, InterruptedException, TimeoutException {
        if (adapter != null) {
            try {
                adapter.stop().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Log exceptions during teardown but don't fail the test
                // Adapter might not have been started or might already be stopped
                System.err.println("Exception during adapter teardown: " + e.getMessage());
            }
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should start and stop successfully")
    void testStartStop() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        CompletableFuture<Void> startFuture = adapter.start();
        assertDoesNotThrow(() -> startFuture.get(5, TimeUnit.SECONDS),
                "Adapter should start without throwing exception");

        // Verify client start was called
        verify(mockQuicClient, times(1)).start();

        // Mock client stop
        when(mockQuicClient.stop()).thenReturn(CompletableFuture.completedFuture(null));

        // Stop adapter
        CompletableFuture<Void> stopFuture = adapter.stop();
        assertDoesNotThrow(() -> stopFuture.get(5, TimeUnit.SECONDS),
                "Adapter should stop without throwing exception");

        // Verify client stop was called
        verify(mockQuicClient, times(1)).stop();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should handle connection events")
    void testConnectionEvents() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Mock connection
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        QuicConnection mockConnection = mock(QuicConnection.class);

        when(mockQuicClient.connect(testAddress))
                .thenReturn(CompletableFuture.completedFuture(mockConnection));

        // Connect
        CompletableFuture<QuicConnection> connectionFuture = adapter.connect(testAddress);
        QuicConnection result = connectionFuture.get(5, TimeUnit.SECONDS);

        // Verify connection was attempted
        verify(mockQuicClient, times(1)).connect(testAddress);
        assertSame(mockConnection, result, "Should return the mocked connection");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should handle disconnection")
    void testDisconnection() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Mock disconnection
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        when(mockQuicClient.disconnect(testAddress))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Disconnect
        CompletableFuture<Void> disconnectFuture = adapter.disconnect(testAddress);
        assertDoesNotThrow(() -> disconnectFuture.get(5, TimeUnit.SECONDS),
                "Disconnection should complete without exception");

        // Verify disconnection was attempted
        verify(mockQuicClient, times(1)).disconnect(testAddress);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should send messages")
    void testMessageSending() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Mock message sending
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        PingMessage pingMessage = new PingMessage();
        when(mockQuicClient.sendMessage(pingMessage, testAddress))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Send message
        CompletableFuture<Void> sendFuture = adapter.sendMessage(pingMessage, testAddress);
        assertDoesNotThrow(() -> sendFuture.get(5, TimeUnit.SECONDS),
                "Message sending should complete without exception");

        // Verify message was sent
        verify(mockQuicClient, times(1)).sendMessage(pingMessage, testAddress);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should send files")
    void testFileSending() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Mock connection and stream
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        QuicConnection mockConnection = mock(QuicConnection.class);
        QuicStream mockStream = mock(QuicStream.class);

        when(mockQuicClient.connect(testAddress))
                .thenReturn(CompletableFuture.completedFuture(mockConnection));
        when(mockConnection.createStream(true))
                .thenReturn(CompletableFuture.completedFuture(mockStream));

        // Test file sending
        Path testFile = Paths.get("test.txt");
        byte[] testData = "Hello, QUIC World!".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        CompletableFuture<Void> sendFuture = adapter.sendFile(testFile, testAddress, testData);
        assertDoesNotThrow(() -> sendFuture.get(5, TimeUnit.SECONDS),
                "File sending should complete without exception");

        // Verify connection and stream creation
        verify(mockQuicClient, times(1)).connect(testAddress);
        verify(mockConnection, times(1)).createStream(true);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should create streams")
    void testStreamCreation() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Mock connection and stream creation
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        QuicConnection mockConnection = mock(QuicConnection.class);
        QuicStream mockStream = mock(QuicStream.class);

        when(mockQuicClient.connect(testAddress))
                .thenReturn(CompletableFuture.completedFuture(mockConnection));
        when(mockConnection.createStream(true))
                .thenReturn(CompletableFuture.completedFuture(mockStream));

        // Create stream
        CompletableFuture<QuicStream> streamFuture = adapter.createStream(testAddress, true);
        QuicStream result = streamFuture.get(5, TimeUnit.SECONDS);

        // Verify connection and stream creation were attempted
        verify(mockQuicClient, times(1)).connect(testAddress);
        verify(mockConnection, times(1)).createStream(true);
        assertSame(mockStream, result, "Should return the mocked stream");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should check connection status")
    void testConnectionStatus() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Test connection status
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        when(mockQuicClient.isConnected(testAddress)).thenReturn(true);

        assertTrue(adapter.isConnected(testAddress), "Should report as connected");

        when(mockQuicClient.isConnected(testAddress)).thenReturn(false);
        assertFalse(adapter.isConnected(testAddress), "Should report as not connected");

        // Verify status checks
        verify(mockQuicClient, times(2)).isConnected(testAddress);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should report active connections")
    void testActiveConnectionCount() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Test active connection count
        when(mockQuicClient.getActiveConnectionCount()).thenReturn(5);

        assertEquals(5, adapter.getActiveConnectionCount(),
                "Should report correct active connection count");

        // Verify count was retrieved
        verify(mockQuicClient, times(1)).getActiveConnectionCount();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should provide configuration")
    void testConfiguration() {
        QuicConfiguration config = adapter.getConfiguration();
        assertSame(configuration, config, "Should return the same configuration instance");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should provide QUIC client")
    void testQuicClient() {
        QuicClient client = adapter.getQuicClient();
        assertNotNull(client, "Should return a QUIC client instance");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should provide statistics")
    void testStatistics() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Mock statistics
        when(mockQuicClient.getActiveConnectionCount()).thenReturn(3);

        QuicTransportAdapter.QuicTransportStatistics stats = adapter.getStatistics();
        assertNotNull(stats, "Statistics should not be null");
        assertEquals(3, stats.getActiveConnections(),
                "Should report correct active connection count");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should handle connection errors")
    void testConnectionErrors() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Mock connection failure
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        RuntimeException testException = new RuntimeException("Connection failed");
        when(mockQuicClient.connect(testAddress))
                .thenReturn(CompletableFuture.failedFuture(testException));

        // Try to connect
        CompletableFuture<QuicConnection> connectionFuture = adapter.connect(testAddress);

        // Should complete with exception
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> connectionFuture.get(5, TimeUnit.SECONDS),
                "Connection should fail with exception");

        // Verify the cause is our test exception
        assertEquals(testException.getMessage(), exception.getCause().getMessage());

        // Verify connection was attempted
        verify(mockQuicClient, times(1)).connect(testAddress);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Adapter should handle message sending errors")
    void testMessageSendingErrors() throws ExecutionException, InterruptedException, TimeoutException {
        // Mock client start
        when(mockQuicClient.start()).thenReturn(CompletableFuture.completedFuture(null));

        // Start adapter
        adapter.start().get(5, TimeUnit.SECONDS);

        // Mock message sending failure
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        PingMessage pingMessage = new PingMessage();
        RuntimeException testException = new RuntimeException("Send failed");
        when(mockQuicClient.sendMessage(pingMessage, testAddress))
                .thenReturn(CompletableFuture.failedFuture(testException));

        // Try to send message
        CompletableFuture<Void> sendFuture = adapter.sendMessage(pingMessage, testAddress);

        // Should complete with exception
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> sendFuture.get(5, TimeUnit.SECONDS),
                "Message sending should fail with exception");

        // Verify the cause is our test exception
        assertEquals(testException.getMessage(), exception.getCause().getMessage());

        // Verify message send was attempted
        verify(mockQuicClient, times(1)).sendMessage(pingMessage, testAddress);
    }
}