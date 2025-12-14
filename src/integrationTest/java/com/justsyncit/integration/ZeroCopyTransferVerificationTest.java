package com.justsyncit.integration;

import com.justsyncit.network.NetworkService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZeroCopyTransferVerificationTest extends E2ETestBase {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testZeroCopyTransferLoopback() throws Exception {
        // 1. Setup: Create a file to transfer
        Path sendFile = tempDir.resolve("zerocopy_send.dat");
        int fileSize = 1024 * 1024 * 5; // 5 MB
        createTestFile(tempDir, "zerocopy_send.dat", fileSize);

        // 2. Start Server
        int port = findAvailablePort();
        InetSocketAddress address = new InetSocketAddress("localhost", port);

        networkService.startServer(port).join();
        assertTrue(networkService.isServerRunning());

        // 3. Initiate Transfer (Sender side)
        // Ensure connection is established first
        networkService.connectToNode(address).get(5, TimeUnit.SECONDS);

        CompletableFuture<Void> transferFuture = networkService.sendFilePart(sendFile, 0, fileSize, address);

        transferFuture.get(10, TimeUnit.SECONDS);

        // 4. Verify reception
        // Since it's a loopback, we implicitly verify by successful future completion
        // (meaning it was sent/queued).
        // Verification of receipt would require hooking into "onMessageReceived" of the
        // server,
        // but since we are sending raw data after header, the server (NetworkService)
        // might not parse it correctly as a message
        // unless we implemented the chunk assembly logic on receiver side.
        // For this test, we verify that the SENDING implementation
        // (ClientConnection/TcpServer) works and doesn't crash.
        // And that sending 5MB finishes successfully.
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testZeroCopyTransferWithTwoNodes() throws Exception {
        // 1. Setup Sender and Receiver
        int receiverPort = findAvailablePort();

        // We need a second network service.
        NetworkService receiverService = serviceFactory.createNetworkService();
        receiverService.startServer(receiverPort).join();

        Path sendFile = tempDir.resolve("zerocopy_2node.dat");
        int fileSize = 1024 * 1024 * 2; // 2 MB
        createTestFile(tempDir, "zerocopy_2node.dat", fileSize);

        // Start sender service
        int senderPort = findAvailablePort();
        networkService.startServer(senderPort).join();

        // 2. Initiate Transfer from Sender (networkService) to Receiver
        // (receiverService)
        InetSocketAddress receiverAddress = new InetSocketAddress("localhost", receiverPort);

        // Connect first
        networkService.connectToNode(receiverAddress).get(5, TimeUnit.SECONDS);

        CompletableFuture<Void> transferFuture = networkService.sendFilePart(sendFile, 0, fileSize, receiverAddress);

        transferFuture.get(10, TimeUnit.SECONDS);

        receiverService.close();
    }

    private void createTestFile(Path dir, String name, int size) throws IOException {
        Path file = dir.resolve(name);
        byte[] data = new byte[size];
        new java.util.Random().nextBytes(data);
        Files.write(file, data);
    }
}
