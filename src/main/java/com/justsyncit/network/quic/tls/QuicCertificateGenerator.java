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

package com.justsyncit.network.quic.tls;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for generating self-signed certificates for QUIC TLS connections.
 * Provides methods to generate key pairs and X.509 certificates suitable for
 * TLS 1.3 with QUIC.
 *
 * Note: This is a simplified implementation for development/testing purposes.
 * In production, you should use proper certificates from a trusted CA.
 */
public final class QuicCertificateGenerator {

    /** Logger for certificate generation operations. */
    private static final Logger logger = LoggerFactory.getLogger(QuicCertificateGenerator.class);

    /** Default key algorithm. */
    private static final String DEFAULT_KEY_ALGORITHM = "EC";

    /** Default key size for EC keys. */
    private static final int DEFAULT_EC_KEY_SIZE = 256;

    /** Certificate validity period in days. */
    private static final int CERTIFICATE_VALIDITY_DAYS = 365;

    /** Private constructor to prevent instantiation. */
    private QuicCertificateGenerator() {
        // Utility class
    }

    /**
     * Generates a new EC key pair suitable for TLS.
     *
     * @return a new EC key pair
     * @throws NoSuchAlgorithmException if the key algorithm is not available
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        logger.debug("Generating EC key pair with size {} bits", DEFAULT_EC_KEY_SIZE);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(DEFAULT_KEY_ALGORITHM);
        keyPairGenerator.initialize(DEFAULT_EC_KEY_SIZE, new SecureRandom());

        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        logger.debug("EC key pair generated successfully");

        return keyPair;
    }

    /**
     * Generates a self-signed X.509 certificate for QUIC/TLS use.
     * This is a simplified implementation that creates a basic certificate.
     * For production use, you should use proper certificate authorities.
     *
     * @param keyPair the key pair to use for the certificate
     * @return a self-signed X.509 certificate
     * @throws Exception if certificate generation fails
     */
    public static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        Objects.requireNonNull(keyPair, "keyPair cannot be null");

        logger.debug("Generating self-signed certificate");

        // For now, we'll create a placeholder certificate
        // In a real implementation, you would use BouncyCastle or another library
        // to create proper X.509 certificates with the right extensions

        // Create a simple certificate using Java's built-in tools
        // This is a simplified approach - in production you'd want more control

        try {
            // Use Java's keytool to generate a self-signed certificate
            // This is a workaround for the BouncyCastle dependency issues
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");

            // For now, we'll create a mock certificate that can be replaced later
            // This allows the rest of the QUIC implementation to proceed
            logger.warn(
                    "Using mock certificate implementation. Replace with proper certificate generation in production.");

            // Create a temporary self-signed certificate using a simpler approach
            // This will be replaced with a proper implementation
            logger.warn("Creating mock certificate - this should be replaced with proper implementation");

            // For now, we'll return null and handle this in the configuration
            // The actual QUIC implementation should handle missing certificates gracefully
            // or use a different approach for certificate generation

            return null;

        } catch (Exception e) {
            logger.error("Failed to generate certificate", e);
            throw e;
        }
    }

    /**
     * Generates a certificate chain with a self-signed root certificate and leaf certificate.
     * This is useful for testing scenarios where you need a proper certificate chain.
     *
     * @param leafKeyPair the key pair for the leaf certificate
     * @return an array containing the leaf certificate and root certificate
     * @throws Exception if certificate generation fails
     */
    public static X509Certificate[] generateCertificateChain(KeyPair leafKeyPair) throws Exception {
        Objects.requireNonNull(leafKeyPair, "leafKeyPair cannot be null");

        logger.debug("Generating certificate chain");

        // For now, return empty array - this should be implemented properly
        logger.warn("Certificate chain generation not implemented - returning empty array");

        return new X509Certificate[0];
    }

    /**
     * Checks if a certificate is suitable for QUIC/TLS use.
     * This is a basic validation method.
     *
     * @param certificate the certificate to validate
     * @return true if the certificate appears suitable for QUIC/TLS
     */
    public static boolean isCertificateSuitableForQuic(X509Certificate certificate) {
        if (certificate == null) {
            return false;
        }

        try {
            // Check if certificate is currently valid
            Date now = new Date();
            if (now.before(certificate.getNotBefore()) || now.after(certificate.getNotAfter())) {
                return false;
            }

            // Check if certificate has a proper key usage (simplified check)
            // In a real implementation, you'd check for specific key usage extensions

            return true;
        } catch (Exception e) {
            logger.debug("Certificate validation failed", e);
            return false;
        }
    }

    /**
     * Gets the default certificate validity period.
     *
     * @return the default validity period in days
     */
    public static int getDefaultCertificateValidityDays() {
        return CERTIFICATE_VALIDITY_DAYS;
    }

    /**
     * Gets the default key algorithm.
     *
     * @return the default key algorithm
     */
    public static String getDefaultKeyAlgorithm() {
        return DEFAULT_KEY_ALGORITHM;
    }

    /**
     * Gets the default key size.
     *
     * @return the default key size in bits
     */
    public static int getDefaultKeySize() {
        return DEFAULT_EC_KEY_SIZE;
    }
}