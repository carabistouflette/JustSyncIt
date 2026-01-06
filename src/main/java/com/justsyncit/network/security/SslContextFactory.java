package com.justsyncit.network.security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Factory for creating SSL contexts.
 * Handles loading of keystores and truststores.
 */
public class SslContextFactory {

    private static final String PROTOCOL = "TLSv1.3";
    private static final String KEY_STORE_TYPE = "PKCS12";

    /**
     * Creates an SSL context from the specified keystore and truststore.
     *
     * @param keyStorePath     path to the keystore file
     * @param keyStorePassword password for the keystore
     * @param trustStorePath   path to the truststore file (optional, uses keystore
     *                         if null)
     * @return the initialized SSL context
     * @throws Exception if an error occurs
     */
    public static SSLContext createSslContext(String keyStorePath, String keyStorePassword, String trustStorePath)
            throws Exception {
        // Load KeyStore
        KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
        try (InputStream keyStoreIs = new FileInputStream(keyStorePath)) {
            keyStore.load(keyStoreIs, keyStorePassword.toCharArray());
        }

        // Initialize KeyManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword.toCharArray());

        // Load TrustStore
        KeyStore trustStore = keyStore; // Default to using keystore as truststore
        if (trustStorePath != null) {
            trustStore = KeyStore.getInstance(KEY_STORE_TYPE);
            try (InputStream trustStoreIs = new FileInputStream(trustStorePath)) {
                trustStore.load(trustStoreIs, keyStorePassword.toCharArray()); // Assuming same password for simplicity
            }
        }

        // Initialize TrustManagerFactory
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Create and initialize SSLContext
        SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return sslContext;
    }
}
