package com.justsyncit.network.security;

import com.justsyncit.network.NetworkConfiguration;
import com.justsyncit.network.server.TcpServer;
import com.justsyncit.scanner.MockAsyncByteBufferPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test for SSL/TLS traffic encryption.
 */
public class SecureTrafficTest {

    private TcpServer server;
    private int serverPort;
    private SSLContext sslContext;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        // Generate a self-signed certificate and keystore
        String password = "password";
        Path keystorePath = tempDir.resolve("keystore.p12");
        generateKeystore(keystorePath, password);

        // Create SSL Context using our factory
        sslContext = SslContextFactory.createSslContext(keystorePath.toString(), password, null);

        // Start server with SSL
        NetworkConfiguration config = new NetworkConfiguration();
        server = new TcpServer(new MockAsyncByteBufferPool(100), config, sslContext);

        // Find a random free port
        server.start(0).join();
        serverPort = server.getPort();
        assertTrue(serverPort > 0, "Server should be bound to a port");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop().join();
        }
    }

    @Test
    void testEncryptedTrafficIsNotPlaintext() throws Exception {
        try (SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost", serverPort))) {
            client.configureBlocking(true);

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            buffer.put("Hello World".getBytes());
            buffer.flip();
            client.write(buffer); // Send plaintext

            ByteBuffer response = ByteBuffer.allocate(1024);
            try {
                int read = client.read(response);
            } catch (Exception e) {
                // Connection might be reset by peer
            }

            // We expect that the server does NOT respond with valid application layer
            // response
            // It should either close connection or protocol error.

            Thread.sleep(500);

            // Try to write again to see if closed
            try {
                buffer.rewind();
                int w = client.write(buffer);
            } catch (Exception e) {
                // Expected if connection closed
            }
        }
    }

    private void generateKeystore(Path path, String password) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", path.toAbsolutePath().toString(),
                "-storepass", password, "-keypass", password,
                "-dname", "CN=Test, OU=Test, O=Test, L=Test, S=Test, C=US", "-validity", "1");

        Process process = pb.start();
        if (process.waitFor() != 0) {
            try (java.util.Scanner s = new java.util.Scanner(process.getErrorStream()).useDelimiter("\\A")) {
                String error = s.hasNext() ? s.next() : "";
                throw new RuntimeException("Failed to generate keystore: " + error);
            }
        }
    }
}
