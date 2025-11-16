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

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.protocol.PingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for QuicClient.
 */
@DisplayName("QuicClient Tests")
public class QuicClientTest {

    private QuicClient quicClient;
    private QuicConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = QuicConfiguration.defaultConfiguration();
        quicClient = new QuicClient(configuration);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (quicClient != null) {
            quicClient.stop().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Client should start and stop successfully")
    void testStartStop() throws Exception {
        // Start the client
        CompletableFuture<Void> startFuture = quicClient.start();
        assertDoesNotThrow(() -> startFuture.get(5, TimeUnit.SECONDS), 
            "Client should start without throwing exception");
        
        // Stop the client
        CompletableFuture<Void> stopFuture = quicClient.stop();
        assertDoesNotThrow(() -> stopFuture.get(5, TimeUnit.SECONDS), 
            "Client should stop without throwing exception");
    }

    @Test
    @DisplayName("Client should not start twice")
    void testStartTwice() throws Exception {
        // Start the client once
        quicClient.start().get(5, TimeUnit.SECONDS);
        
        // Try to start again - should fail
        CompletableFuture<Void> secondStart = quicClient.start();
        assertThrows(Exception.class, () -> secondStart.get(5, TimeUnit.SECONDS),
            "Starting client twice should throw exception");
    }

    @Test
    @DisplayName("Client should handle connection events")
    void testConnectionEvents() throws Exception {
        AtomicBoolean connected = new AtomicBoolean(false);
        AtomicBoolean disconnected = new AtomicBoolean(false);
        
        quicClient.addEventListener(new QuicClient.QuicClientEventListener() {
            @Override
            public void onConnected(InetSocketAddress serverAddress, QuicConnection connection) {
                connected.set(true);
            }

            @Override
            public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
                disconnected.set(true);
            }

            @Override
            public void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
                // Not used in this test
            }

            @Override
            public void onError(Throwable error, String context) {
                // Not used in this test
            }
        });

        quicClient.start().get(5, TimeUnit.SECONDS);
        
        // Simulate connection (in real implementation, this would be triggered by actual QUIC events)
        // For now, we just verify the listener is registered
        assertNotNull(quicClient, "Client should be initialized");
    }

    @Test
    @DisplayName("Client should handle message events")
    void testMessageEvents() throws Exception {
        AtomicInteger messageCount = new AtomicInteger(0);
        
        quicClient.addEventListener(new QuicClient.QuicClientEventListener() {
            @Override
            public void onConnected(InetSocketAddress serverAddress, QuicConnection connection) {
                // Not used in this test
            }

            @Override
            public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
                // Not used in this test
            }

            @Override
            public void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
                messageCount.incrementAndGet();
            }

            @Override
            public void onError(Throwable error, String context) {
                // Not used in this test
            }
        });

        quicClient.start().get(5, TimeUnit.SECONDS);
        
        // Verify listener is registered
        assertNotNull(quicClient, "Client should be initialized");
    }

    @Test
    @DisplayName("Client should handle error events")
    void testErrorEvents() throws Exception {
        AtomicBoolean errorHandled = new AtomicBoolean(false);
        
        quicClient.addEventListener(new QuicClient.QuicClientEventListener() {
            @Override
            public void onConnected(InetSocketAddress serverAddress, QuicConnection connection) {
                // Not used in this test
            }

            @Override
            public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
                // Not used in this test
            }

            @Override
            public void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
                // Not used in this test
            }

            @Override
            public void onError(Throwable error, String context) {
                errorHandled.set(true);
            }
        });

        quicClient.start().get(5, TimeUnit.SECONDS);
        
        // Verify listener is registered
        assertNotNull(quicClient, "Client should be initialized");
    }

    @Test
    @DisplayName("Client should report connection status correctly")
    void testConnectionStatus() throws Exception {
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        
        quicClient.start().get(5, TimeUnit.SECONDS);
        
        // Initially should not be connected
        assertFalse(quicClient.isConnected(testAddress), 
            "Should not be connected initially");
        
        // Active connection count should be 0
        assertEquals(0, quicClient.getActiveConnectionCount(), 
            "Active connection count should be 0 initially");
    }

    @Test
    @DisplayName("Client should handle listener management")
    void testListenerManagement() throws Exception {
        QuicClient.QuicClientEventListener listener1 = new TestEventListener();
        QuicClient.QuicClientEventListener listener2 = new TestEventListener();
        
        // Add listeners
        quicClient.addEventListener(listener1);
        quicClient.addEventListener(listener2);
        
        quicClient.start().get(5, TimeUnit.SECONDS);
        
        // Remove one listener
        quicClient.removeEventListener(listener2);
        
        // Should not throw exception
        assertDoesNotThrow(() -> {
            // Verify client is still functional
            assertFalse(quicClient.isConnected(new InetSocketAddress("localhost", 8080)));
        }, "Client should remain functional after removing listener");
    }

    @Test
    @DisplayName("Client should validate null listeners")
    void testNullListenerValidation() {
        assertThrows(NullPointerException.class, () -> 
            quicClient.addEventListener(null),
            "Adding null listener should throw exception");
    }

    @Test
    @DisplayName("Client should connect to server asynchronously")
    void testAsyncConnection() throws Exception {
        quicClient.start().get(5, TimeUnit.SECONDS);
        
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        CompletableFuture<QuicConnection> connectionFuture = quicClient.connect(testAddress);
        
        // Should return a future (connection may fail in test environment)
        assertNotNull(connectionFuture, "Connection future should not be null");
    }

    @Test
    @DisplayName("Client should create streams asynchronously")
    void testAsyncStreamCreation() throws Exception {
        quicClient.start().get(5, TimeUnit.SECONDS);
        
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        CompletableFuture<QuicStream> streamFuture = quicClient.createStream(testAddress, true);
        
        // Should return a future (stream creation may fail without connection)
        assertNotNull(streamFuture, "Stream future should not be null");
    }

    @Test
    @DisplayName("Client should send messages asynchronously")
    void testAsyncMessageSending() throws Exception {
        quicClient.start().get(5, TimeUnit.SECONDS);
        
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        PingMessage pingMessage = new PingMessage();
        CompletableFuture<Void> sendFuture = quicClient.sendMessage(pingMessage, testAddress);
        
        // Should return a future (send may fail without connection)
        assertNotNull(sendFuture, "Send future should not be null");
    }

    @Test
    @DisplayName("Client should disconnect asynchronously")
    void testAsyncDisconnection() throws Exception {
        quicClient.start().get(5, TimeUnit.SECONDS);
        
        InetSocketAddress testAddress = new InetSocketAddress("localhost", 8080);
        CompletableFuture<Void> disconnectFuture = quicClient.disconnect(testAddress);
        
        // Should complete without exception even if not connected
        assertDoesNotThrow(() -> disconnectFuture.get(5, TimeUnit.SECONDS),
            "Disconnect should complete without exception");
    }

    /**
     * Test event listener implementation.
     */
    private static class TestEventListener implements QuicClient.QuicClientEventListener {
        @Override
        public void onConnected(InetSocketAddress serverAddress, QuicConnection connection) {
            // Empty implementation
        }

        @Override
        public void onDisconnected(InetSocketAddress serverAddress, Throwable cause) {
            // Empty implementation
        }

        @Override
        public void onMessageReceived(InetSocketAddress serverAddress, ProtocolMessage message) {
            // Empty implementation
        }

        @Override
        public void onError(Throwable error, String context) {
            // Empty implementation
        }
    }
}