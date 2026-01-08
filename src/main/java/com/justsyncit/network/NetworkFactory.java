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
    private com.justsyncit.auth.MasterPasswordService masterPasswordService;

    public NetworkFactory(SecurityModule securityModule) {
        this.securityModule = securityModule;
        this.networkModule = new NetworkModule(securityModule);
    }

    public NetworkFactory() {
        this(new SecurityModule());
    }

    public void setMasterPasswordService(com.justsyncit.auth.MasterPasswordService masterPasswordService) {
        this.masterPasswordService = masterPasswordService;
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
            validateClusterKey(envKey);
            return networkModule.createNetworkService(blake3Service, envKey, null, masterPasswordService);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) { // Assuming ServiceException or other checked
            throw new RuntimeException("Failed to create network service", e);
        }
    }

    /**
     * Validates the cluster key for proper format and length.
     * The key must be a valid Base64 string that decodes to exactly 32 bytes (256
     * bits).
     *
     * @param clusterKey the cluster key to validate
     * @throws IllegalArgumentException if the key is invalid
     */
    private void validateClusterKey(String clusterKey) {
        if (clusterKey == null || clusterKey.isBlank()) {
            // Allow null key - network service may operate in local-only mode
            return;
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(clusterKey);
            if (decoded.length != 32) {
                throw new IllegalArgumentException(
                        "Cluster key must be exactly 32 bytes (256 bits) for AES-256. Got: " + decoded.length
                                + " bytes. " +
                                "Generate a valid key with: openssl rand -base64 32");
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("32 bytes")) {
                throw e; // Re-throw our size validation error
            }
            throw new IllegalArgumentException(
                    "Cluster key must be a valid Base64 encoded string. " +
                            "Generate a valid key with: openssl rand -base64 32",
                    e);
        }
    }

    public NetworkService createNetworkService(Blake3Service blake3Service,
            com.justsyncit.storage.metadata.MetadataService metadataService) {
        try {
            String envKey = System.getenv("JUSTSYNCIT_CLUSTER_KEY");
            if (envKey == null) {
                envKey = System.getProperty("justsyncit.cluster.key");
            }
            return networkModule.createNetworkService(blake3Service, envKey, metadataService, masterPasswordService);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create network service", e);
        }
    }

    public NetworkService createNetworkService(Blake3Service blake3Service, String clusterKey) {
        try {
            return networkModule.createNetworkService(blake3Service, clusterKey, null, masterPasswordService);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create network service", e);
        }
    }

    public NetworkService createNetworkService(Blake3Service blake3Service, String clusterKey,
            com.justsyncit.storage.metadata.MetadataService metadataService) {
        try {
            return networkModule.createNetworkService(blake3Service, clusterKey, metadataService,
                    masterPasswordService);
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
