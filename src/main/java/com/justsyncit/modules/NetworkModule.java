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

        // Retrieve key from Env or use default (Insecure fallback for Omega demo)
        String envKey = System.getenv("JUSTSYNCIT_CLUSTER_KEY");
        byte[] clusterKey;
        if (envKey != null && !envKey.isEmpty()) {
            clusterKey = java.util.Base64.getDecoder().decode(envKey);
        } else {
            clusterKey = new byte[32];
            System.err.println("[WARNING] using INSECURE default cluster key. Set JUSTSYNCIT_CLUSTER_KEY.");
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
