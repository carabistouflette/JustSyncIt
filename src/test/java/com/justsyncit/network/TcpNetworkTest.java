package com.justsyncit.network;

import com.justsyncit.network.client.TcpClient;
import com.justsyncit.network.connection.Connection;
import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.server.TcpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TcpNetworkTest {

    private TcpServer server;
    private TcpClient client;
    private NetworkConfiguration config;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        config = new NetworkConfiguration();
        server = new TcpServer(config);
        client = new TcpClient(config);
        port = 28495;
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testHonestTcpConnection() throws Exception {
        // Start server
        server.start(port).get(5, TimeUnit.SECONDS);
        assertTrue(server.isRunning(), "Server should be running");

        // Verify port binding (honest check)
        assertEquals(port, server.getPort());

        // Connect client
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
        CompletableFuture<Void> connectFuture = client.connect(address);

        connectFuture.get(5, TimeUnit.SECONDS);

        assertTrue(client.isConnected(address), "Client should be connected");

        // Give server time to register connection
        Thread.sleep(200);
        assertEquals(1, server.getConnectionCount(), "Server should have 1 active connection");

        // Test Message Exchange
        long timestamp = System.currentTimeMillis();
        com.justsyncit.network.protocol.PingMessage message = new com.justsyncit.network.protocol.PingMessage(
                timestamp);

        AtomicReference<ProtocolMessage> receivedRef = new AtomicReference<>();

        // Setup server listener to capture message
        server.addServerEventListener(new TcpServer.ServerEventListener() {
            @Override
            public void onClientConnected(InetSocketAddress clientAddress) {
            }

            @Override
            public void onClientDisconnected(InetSocketAddress clientAddress, Throwable cause) {
            }

            @Override
            public void onMessageReceived(InetSocketAddress clientAddress, ProtocolMessage msg) {
                receivedRef.set(msg);
            }

            @Override
            public void onError(Throwable error, String context) {
            }
        });

        // Send message from client
        client.sendMessage(message, address).get(5, TimeUnit.SECONDS);

        // Wait for receipt
        long deadline = System.currentTimeMillis() + 5000;
        while (receivedRef.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertNotNull(receivedRef.get(), "Server should have received message");
        ProtocolMessage received = receivedRef.get();
        assertTrue(received instanceof com.justsyncit.network.protocol.PingMessage, "Should receive PingMessage");
        assertEquals(timestamp, ((com.justsyncit.network.protocol.PingMessage) received).getTimestamp(),
                "Timestamp mismatch");
    }

    @Test
    void testConnectionRefused() {
        int badPort = 29999;
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", badPort);

        Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, () -> {
            client.connect(address).get(5, TimeUnit.SECONDS);
        });

        // Honest TCP must throw IOException (Connection refused) wrapped in
        // ExecutionException
        // The type might be implicitly wrapped or chained.
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof java.io.IOException,
                "Should fail with IOException (Connection refused). Got: " + cause.getClass().getName());
    }
}
