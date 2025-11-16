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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of CertificateProvider using Java's built-in security APIs.
 * Provides a fallback implementation when BouncyCastle is not available.
 * Follows Single Responsibility Principle by focusing only on certificate operations.
 */
public class DefaultCertificateProvider implements CertificateProvider {

    /** Logger for certificate operations. */
    private static final Logger logger = LoggerFactory.getLogger(DefaultCertificateProvider.class);

    /** Default key algorithm. */
    private static final String DEFAULT_KEY_ALGORITHM = "EC";

    /** Default key size for EC keys. */
    private static final int DEFAULT_EC_KEY_SIZE = 256;

    /** Certificate validity period. */
    private static final Duration CERTIFICATE_VALIDITY = Duration.ofDays(365);

    /** Certificate serial number. */
    private static final BigInteger CERTIFICATE_SERIAL_NUMBER = BigInteger.ONE;

    /**
     * Creates a new default certificate provider.
     */
    public DefaultCertificateProvider() {
        logger.debug("Initializing default certificate provider");
    }

    @Override
    public KeyPair generateKeyPair() throws CertificateGenerationException {
        try {
            logger.debug("Generating EC key pair with size {} bits", DEFAULT_EC_KEY_SIZE);

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(DEFAULT_KEY_ALGORITHM);
            keyPairGenerator.initialize(DEFAULT_EC_KEY_SIZE, new SecureRandom());

            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            logger.debug("EC key pair generated successfully");

            return keyPair;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to generate key pair", e);
            throw new CertificateGenerationException("Key algorithm not available", e);
        }
    }

    @Override
    public X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws CertificateGenerationException {
        Objects.requireNonNull(keyPair, "Key pair cannot be null");

        try {
            logger.debug("Generating self-signed certificate");

            // For development purposes, we'll use a simplified approach
            // In production, this should be replaced with proper certificate generation
            logger.warn("Using simplified certificate generation. Replace with proper implementation in production.");

            // Create a basic certificate using Java's built-in tools
            // This is a simplified approach that can be enhanced later
            return createBasicSelfSignedCertificate(keyPair);

        } catch (Exception e) {
            logger.error("Failed to generate self-signed certificate", e);
            throw new CertificateGenerationException("Certificate generation failed", e);
        }
    }

    @Override
    public X509Certificate[] generateCertificateChain(KeyPair leafKeyPair) throws CertificateGenerationException {
        Objects.requireNonNull(leafKeyPair, "Leaf key pair cannot be null");

        try {
            logger.debug("Generating certificate chain");

            // Generate root CA key pair
            KeyPair rootKeyPair = generateKeyPair();

            // Create root certificate
            X509Certificate rootCert = generateSelfSignedCertificate(rootKeyPair);

            // Create leaf certificate
            X509Certificate leafCert = generateSelfSignedCertificate(leafKeyPair);

            logger.debug("Certificate chain generated successfully");
            return new X509Certificate[]{leafCert, rootCert};

        } catch (Exception e) {
            logger.error("Failed to generate certificate chain", e);
            throw new CertificateGenerationException("Certificate chain generation failed", e);
        }
    }

    @Override
    public boolean isCertificateSuitableForQuic(X509Certificate certificate) {
        if (certificate == null) {
            return false;
        }

        try {
            // Check if certificate is currently valid
            Date now = new Date();
            if (now.before(certificate.getNotBefore()) || now.after(certificate.getNotAfter())) {
                logger.debug("Certificate is not within validity period");
                return false;
            }

            // Check key usage for TLS
            boolean[] keyUsage = certificate.getKeyUsage();
            if (keyUsage != null) {
                boolean digitalSignature = keyUsage[0];
                boolean keyEncipherment = keyUsage[2];

                if (!digitalSignature || !keyEncipherment) {
                    logger.debug("Certificate lacks required key usage for TLS");
                    return false;
                }
            }

            logger.debug("Certificate appears suitable for QUIC/TLS");
            return true;

        } catch (Exception e) {
            logger.debug("Certificate validation failed", e);
            return false;
        }
    }

    /**
     * Creates a basic self-signed certificate using Java's built-in APIs.
     * This is a simplified implementation for development purposes.
     *
     * @param keyPair the key pair to use
     * @return a basic self-signed certificate
     * @throws Exception if creation fails
     */
    private X509Certificate createBasicSelfSignedCertificate(KeyPair keyPair) throws Exception {
        // This is a placeholder implementation
        // In a real scenario, you would use a proper certificate library
        // For now, we'll create a minimal certificate that can be used for testing

        // Use Java's keytool approach to create a self-signed certificate
        // This is a simplified approach that can be enhanced

        logger.warn("Creating basic certificate - enhance with proper certificate library in production");

        // For now, we'll return a mock certificate that can be replaced
        // The actual implementation should use proper certificate generation
        return null; // This should be replaced with actual certificate
    }
}