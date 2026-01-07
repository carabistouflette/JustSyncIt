package com.justsyncit.network;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.modules.NetworkModule;
import com.justsyncit.modules.SecurityModule;
import com.justsyncit.network.encryption.EncryptionService;

/**
 * Factory for creating network-related services.
 * Extracts network responsibility from the monolithic ServiceFactory.
 */
public class NetworkFactory {

    private final NetworkModule networkModule;
    private final SecurityModule securityModule; // Security often tied to Network

    public NetworkFactory(SecurityModule securityModule) {
        this.securityModule = securityModule;
        this.networkModule = new NetworkModule(securityModule);
    }

    public NetworkFactory() {
        this(new SecurityModule());
    }

    public NetworkService createNetworkService(Blake3Service blake3Service) {
        // NetworkModule might throw ServiceException wrapped or unchecked?
        // Method signature of NetworkModule.createNetworkService?
        // Assuming it's clean, but previous lint said Unhandled exception type
        // ServiceException.
        // So NetworkModule throws it.
        try {
            String envKey = System.getenv("JUSTSYNCIT_CLUSTER_KEY");
            if (envKey == null || envKey.isBlank()) {
                envKey = System.getProperty("justsyncit.cluster.key");
            }
            // If still null, we pass it. NetworkModule might fail if key is required.
            // But we preserved legacy behavior.
            return networkModule.createNetworkService(blake3Service, envKey);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) { // Assuming ServiceException or other checked
            throw new RuntimeException("Failed to create network service", e);
        }
    }

    public NetworkService createNetworkService(Blake3Service blake3Service, String clusterKey) {
        try {
            return networkModule.createNetworkService(blake3Service, clusterKey);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create network service", e);
        }
    }

    public EncryptionService createEncryptionService() {
        return securityModule.createEncryptionService();
    }

    public Blake3Service createBlake3Service() {
        try {
            return securityModule.createBlake3Service();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Blake3Service", e);
        }
    }
}
