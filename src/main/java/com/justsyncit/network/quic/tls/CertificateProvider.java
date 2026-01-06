package com.justsyncit.network.quic.tls;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

/**
 * Provider interface for TLS certificates and key pairs.
 * Follows Dependency Inversion Principle by abstracting certificate generation.
 */
public interface CertificateProvider {

    /**
     * Generates a new key pair suitable for TLS.
     *
     * @return a new key pair
     * @throws CertificateGenerationException if key generation fails
     */
    KeyPair generateKeyPair() throws CertificateGenerationException;

    /**
     * Generates a self-signed certificate for the given key pair.
     *
     * @param keyPair the key pair to use
     * @return a self-signed certificate
     * @throws CertificateGenerationException if certificate generation fails
     */
    X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws CertificateGenerationException;

    /**
     * Generates a certificate chain with root and leaf certificates.
     *
     * @param leafKeyPair the key pair for the leaf certificate
     * @return an array containing the certificate chain
     * @throws CertificateGenerationException if certificate generation fails
     */
    X509Certificate[] generateCertificateChain(KeyPair leafKeyPair) throws CertificateGenerationException;

    /**
     * Validates if a certificate is suitable for QUIC/TLS use.
     *
     * @param certificate the certificate to validate
     * @return true if suitable, false otherwise
     */
    boolean isCertificateSuitableForQuic(X509Certificate certificate);
}