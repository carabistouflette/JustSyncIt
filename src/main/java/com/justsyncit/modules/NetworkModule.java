package com.justsyncit.modules;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.NetworkServiceImpl;
import com.justsyncit.network.client.TcpClient;
import com.justsyncit.network.connection.ConnectionManager;
import com.justsyncit.network.connection.ConnectionManagerImpl;
import com.justsyncit.network.server.TcpServer;
import com.justsyncit.network.transfer.FileTransferManagerImpl;

/**
 * Module responsible for creating network-related services.
 */
public class NetworkModule {

    private final SecurityModule securityModule;

    public NetworkModule(SecurityModule securityModule) {
        this.securityModule = securityModule;
    }

    public NetworkService createNetworkService(Blake3Service blake3Service) {
        com.justsyncit.network.NetworkConfiguration configuration = new com.justsyncit.network.NetworkConfiguration();
        TcpServer tcpServer = new TcpServer(configuration);
        TcpClient tcpClient = new TcpClient(configuration);
        ConnectionManager connectionManager = new ConnectionManagerImpl();
        FileTransferManagerImpl fileTransferManager = new FileTransferManagerImpl();
        fileTransferManager.setBlake3Service(blake3Service);
        // Inject dependencies for DIP
        fileTransferManager.setTransferPipelineFactory(
                new com.justsyncit.network.transfer.pipeline.DefaultTransferPipelineFactory());

        com.justsyncit.network.encryption.EncryptionService encryptionService = securityModule
                .createEncryptionService();

        String envKey = System.getenv("JUSTSYNCIT_CLUSTER_KEY");
        byte[] clusterKey;
        if (envKey != null && !envKey.isEmpty()) {
            try {
                clusterKey = java.util.Base64.getDecoder().decode(envKey);
                if (clusterKey.length != 32) {
                    throw new IllegalArgumentException(
                            "JUSTSYNCIT_CLUSTER_KEY must decode to exactly 32 bytes. Got: " + clusterKey.length);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid JUSTSYNCIT_CLUSTER_KEY: " + e.getMessage(), e);
            }
        } else {
            // Allow explicit opt-in for insecure mode in development/testing ONLY
            String allowInsecure = System.getenv("JUSTSYNCIT_ALLOW_INSECURE");
            if ("true".equalsIgnoreCase(allowInsecure)) {
                clusterKey = new byte[32];
                java.security.SecureRandom random = new java.security.SecureRandom();
                random.nextBytes(clusterKey); // At least use random bytes, not zeros
                System.err.println("[SECURITY WARNING] Running with randomly generated ephemeral cluster key.");
                System.err.println("[SECURITY WARNING] Network encryption will NOT be interoperable across restarts!");
                System.err.println("[SECURITY WARNING] Set JUSTSYNCIT_CLUSTER_KEY for production use.");
            } else {
                throw new IllegalStateException(
                        "JUSTSYNCIT_CLUSTER_KEY environment variable is required for secure network operation. " +
                                "Generate one with: openssl rand -base64 32 | tr -d '\\n' && echo");
            }
        }

        return new NetworkServiceImpl(tcpServer, tcpClient, fileTransferManager, connectionManager, blake3Service,
                new com.justsyncit.network.quic.adapter.QuicTransportAdapter(
                        com.justsyncit.network.quic.QuicConfiguration.defaultConfiguration()),
                com.justsyncit.network.quic.QuicConfiguration.defaultConfiguration(),
                com.justsyncit.network.TransportType.TCP,
                encryptionService,
                clusterKey);
    }
}
