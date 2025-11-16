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