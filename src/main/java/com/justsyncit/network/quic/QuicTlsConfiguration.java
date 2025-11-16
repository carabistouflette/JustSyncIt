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

package com.justsyncit.network.quic;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.justsyncit.network.quic.tls.CertificateProvider;
import com.justsyncit.network.quic.tls.DefaultCertificateProvider;

/**
 * Configuration for TLS 1.3 settings used by QUIC connections.
 * Provides comprehensive TLS configuration including certificate management,
 * cipher suites, and security policies.
 */
public class QuicTlsConfiguration {

    /** Default TLS version. */
    public static final String DEFAULT_TLS_VERSION = "TLSv1.3";
    
    /** Default cipher suites for TLS 1.3. */
    public static final List<String> DEFAULT_CIPHER_SUITES = Arrays.asList(
        "TLS_AES_256_GCM_SHA384",
        "TLS_AES_128_GCM_SHA256",
        "TLS_CHACHA20_POLY1305_SHA256"
    );
    
    /** Default application layer protocols. */
    public static final List<String> DEFAULT_APPLICATION_PROTOCOLS = Arrays.asList(
        "justsyncit/1.0"
    );
    
    /** Default session timeout. */
    public static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofHours(1);
    
    /** Default session ticket lifetime. */
    public static final Duration DEFAULT_SESSION_TICKET_LIFETIME = Duration.ofDays(7);

    /** TLS version to use. */
    private final String tlsVersion;
    
    /** List of enabled cipher suites. */
    private final List<String> cipherSuites;
    
    /** List of application layer protocols (ALPN). */
    private final List<String> applicationProtocols;
    
    /** Server certificate chain. */
    private final List<X509Certificate> serverCertificates;
    
    /** Server private key. */
    private final PrivateKey serverPrivateKey;
    
    /** Client certificate chain (for client authentication). */
    private final List<X509Certificate> clientCertificates;
    
    /** Client private key (for client authentication). */
    private final PrivateKey clientPrivateKey;
    
    /** Trusted CA certificates. */
    private final List<X509Certificate> trustedCertificates;
    
    /** Whether to verify peer certificates. */
    private final boolean verifyPeer;
    
    /** Session timeout. */
    private final Duration sessionTimeout;
    
    /** Session ticket lifetime. */
    private final Duration sessionTicketLifetime;
    
    /** Whether to enable session resumption. */
    private final boolean enableSessionResumption;
    
    /** Whether to enable 0-RTT data. */
    private final boolean enableZeroRtt;

    private QuicTlsConfiguration(Builder builder) {
        this.tlsVersion = Objects.requireNonNull(builder.tlsVersion, "tlsVersion cannot be null");
        this.cipherSuites = Collections.unmodifiableList(Objects.requireNonNull(builder.cipherSuites, "cipherSuites cannot be null"));
        this.applicationProtocols = Collections.unmodifiableList(Objects.requireNonNull(builder.applicationProtocols, "applicationProtocols cannot be null"));
        this.serverCertificates = Collections.unmodifiableList(Objects.requireNonNull(builder.serverCertificates, "serverCertificates cannot be null"));
        this.serverPrivateKey = Objects.requireNonNull(builder.serverPrivateKey, "serverPrivateKey cannot be null");
        this.clientCertificates = Collections.unmodifiableList(Objects.requireNonNull(builder.clientCertificates, "clientCertificates cannot be null"));
        this.clientPrivateKey = builder.clientPrivateKey;
        this.trustedCertificates = Collections.unmodifiableList(Objects.requireNonNull(builder.trustedCertificates, "trustedCertificates cannot be null"));
        this.verifyPeer = builder.verifyPeer;
        this.sessionTimeout = Objects.requireNonNull(builder.sessionTimeout, "sessionTimeout cannot be null");
        this.sessionTicketLifetime = Objects.requireNonNull(builder.sessionTicketLifetime, "sessionTicketLifetime cannot be null");
        this.enableSessionResumption = builder.enableSessionResumption;
        this.enableZeroRtt = builder.enableZeroRtt;
    }

    /**
     * Gets the TLS version.
     *
     * @return the TLS version
     */
    public String getTlsVersion() {
        return tlsVersion;
    }

    /**
     * Gets the cipher suites.
     *
     * @return the cipher suites
     */
    public List<String> getCipherSuites() {
        return cipherSuites;
    }

    /**
     * Gets the application layer protocols.
     *
     * @return the application protocols
     */
    public List<String> getApplicationProtocols() {
        return applicationProtocols;
    }

    /**
     * Gets the server certificates.
     *
     * @return the server certificates
     */
    public List<X509Certificate> getServerCertificates() {
        return serverCertificates;
    }

    /**
     * Gets the server private key.
     *
     * @return the server private key
     */
    public PrivateKey getServerPrivateKey() {
        return serverPrivateKey;
    }

    /**
     * Gets the client certificates.
     *
     * @return the client certificates
     */
    public List<X509Certificate> getClientCertificates() {
        return clientCertificates;
    }

    /**
     * Gets the client private key.
     *
     * @return the client private key
     */
    public PrivateKey getClientPrivateKey() {
        return clientPrivateKey;
    }

    /**
     * Gets the trusted certificates.
     *
     * @return the trusted certificates
     */
    public List<X509Certificate> getTrustedCertificates() {
        return trustedCertificates;
    }

    /**
     * Checks if peer verification is enabled.
     *
     * @return true if peer verification is enabled
     */
    public boolean isVerifyPeer() {
        return verifyPeer;
    }

    /**
     * Gets the session timeout.
     *
     * @return the session timeout
     */
    public Duration getSessionTimeout() {
        return sessionTimeout;
    }

    /**
     * Gets the session ticket lifetime.
     *
     * @return the session ticket lifetime
     */
    public Duration getSessionTicketLifetime() {
        return sessionTicketLifetime;
    }

    /**
     * Checks if session resumption is enabled.
     *
     * @return true if session resumption is enabled
     */
    public boolean isEnableSessionResumption() {
        return enableSessionResumption;
    }

    /**
     * Checks if 0-RTT is enabled.
     *
     * @return true if 0-RTT is enabled
     */
    public boolean isEnableZeroRtt() {
        return enableZeroRtt;
    }

    /**
     * Creates a new builder for TLS configuration.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default TLS configuration with self-signed certificates.
     *
     * @return a default configuration
     */
    public static QuicTlsConfiguration defaultConfiguration() {
        return defaultConfiguration(new DefaultCertificateProvider());
    }
    
    /**
     * Creates a default TLS configuration with the specified certificate provider.
     *
     * @param certificateProvider the certificate provider to use
     * @return a default configuration
     */
    public static QuicTlsConfiguration defaultConfiguration(CertificateProvider certificateProvider) {
        Objects.requireNonNull(certificateProvider, "certificateProvider cannot be null");
        
        try {
            KeyPair keyPair = certificateProvider.generateKeyPair();
            X509Certificate certificate = certificateProvider.generateSelfSignedCertificate(keyPair);
            
            // Handle the case where certificate generation returns null
            if (certificate == null) {
                // Create a configuration without certificates for development
                // In production, this should be replaced with proper certificates
                return builder()
                    .verifyPeer(false) // Disable peer verification for development
                    .serverCertificates(Collections.emptyList())
                    .serverPrivateKey(keyPair.getPrivate())
                    .trustedCertificates(Collections.emptyList())
                    .build();
            }
            
            return builder()
                .serverCertificates(Arrays.asList(certificate))
                .serverPrivateKey(keyPair.getPrivate())
                .trustedCertificates(Arrays.asList(certificate))
                .build();
        } catch (CertificateGenerationException e) {
            throw new RuntimeException("Failed to create default TLS configuration", e);
        }
    }

    /**
     * Builder for QuicTlsConfiguration.
     */
    public static class Builder {
        private String tlsVersion = DEFAULT_TLS_VERSION;
        private List<String> cipherSuites = DEFAULT_CIPHER_SUITES;
        private List<String> applicationProtocols = DEFAULT_APPLICATION_PROTOCOLS;
        private List<X509Certificate> serverCertificates = Collections.emptyList();
        private PrivateKey serverPrivateKey;
        private List<X509Certificate> clientCertificates = Collections.emptyList();
        private PrivateKey clientPrivateKey;
        private List<X509Certificate> trustedCertificates = Collections.emptyList();
        private boolean verifyPeer = true;
        private Duration sessionTimeout = DEFAULT_SESSION_TIMEOUT;
        private Duration sessionTicketLifetime = DEFAULT_SESSION_TICKET_LIFETIME;
        private boolean enableSessionResumption = true;
        private boolean enableZeroRtt = true;

        /**
         * Sets the TLS version.
         *
         * @param tlsVersion the TLS version
         * @return this builder
         */
        public Builder tlsVersion(String tlsVersion) {
            this.tlsVersion = Objects.requireNonNull(tlsVersion, "tlsVersion cannot be null");
            return this;
        }

        /**
         * Sets the cipher suites.
         *
         * @param cipherSuites the cipher suites
         * @return this builder
         */
        public Builder cipherSuites(List<String> cipherSuites) {
            this.cipherSuites = Objects.requireNonNull(cipherSuites, "cipherSuites cannot be null");
            return this;
        }

        /**
         * Sets the application layer protocols.
         *
         * @param applicationProtocols the application protocols
         * @return this builder
         */
        public Builder applicationProtocols(List<String> applicationProtocols) {
            this.applicationProtocols = Objects.requireNonNull(applicationProtocols, "applicationProtocols cannot be null");
            return this;
        }

        /**
         * Sets the server certificates.
         *
         * @param serverCertificates the server certificates
         * @return this builder
         */
        public Builder serverCertificates(List<X509Certificate> serverCertificates) {
            this.serverCertificates = Objects.requireNonNull(serverCertificates, "serverCertificates cannot be null");
            return this;
        }

        /**
         * Sets the server private key.
         *
         * @param serverPrivateKey the server private key
         * @return this builder
         */
        public Builder serverPrivateKey(PrivateKey serverPrivateKey) {
            this.serverPrivateKey = Objects.requireNonNull(serverPrivateKey, "serverPrivateKey cannot be null");
            return this;
        }

        /**
         * Sets the client certificates.
         *
         * @param clientCertificates the client certificates
         * @return this builder
         */
        public Builder clientCertificates(List<X509Certificate> clientCertificates) {
            this.clientCertificates = Objects.requireNonNull(clientCertificates, "clientCertificates cannot be null");
            return this;
        }

        /**
         * Sets the client private key.
         *
         * @param clientPrivateKey the client private key
         * @return this builder
         */
        public Builder clientPrivateKey(PrivateKey clientPrivateKey) {
            this.clientPrivateKey = clientPrivateKey;
            return this;
        }

        /**
         * Sets the trusted certificates.
         *
         * @param trustedCertificates the trusted certificates
         * @return this builder
         */
        public Builder trustedCertificates(List<X509Certificate> trustedCertificates) {
            this.trustedCertificates = Objects.requireNonNull(trustedCertificates, "trustedCertificates cannot be null");
            return this;
        }

        /**
         * Enables or disables peer verification.
         *
         * @param verifyPeer true to enable peer verification
         * @return this builder
         */
        public Builder verifyPeer(boolean verifyPeer) {
            this.verifyPeer = verifyPeer;
            return this;
        }

        /**
         * Sets the session timeout.
         *
         * @param sessionTimeout the session timeout
         * @return this builder
         */
        public Builder sessionTimeout(Duration sessionTimeout) {
            this.sessionTimeout = Objects.requireNonNull(sessionTimeout, "sessionTimeout cannot be null");
            return this;
        }

        /**
         * Sets the session ticket lifetime.
         *
         * @param sessionTicketLifetime the session ticket lifetime
         * @return this builder
         */
        public Builder sessionTicketLifetime(Duration sessionTicketLifetime) {
            this.sessionTicketLifetime = Objects.requireNonNull(sessionTicketLifetime, "sessionTicketLifetime cannot be null");
            return this;
        }

        /**
         * Enables or disables session resumption.
         *
         * @param enableSessionResumption true to enable session resumption
         * @return this builder
         */
        public Builder enableSessionResumption(boolean enableSessionResumption) {
            this.enableSessionResumption = enableSessionResumption;
            return this;
        }

        /**
         * Enables or disables 0-RTT.
         *
         * @param enableZeroRtt true to enable 0-RTT
         * @return this builder
         */
        public Builder enableZeroRtt(boolean enableZeroRtt) {
            this.enableZeroRtt = enableZeroRtt;
            return this;
        }

        /**
         * Builds the TLS configuration.
         *
         * @return the configuration
         */
        public QuicTlsConfiguration build() {
            return new QuicTlsConfiguration(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuicTlsConfiguration that = (QuicTlsConfiguration) o;
        return verifyPeer == that.verifyPeer &&
               enableSessionResumption == that.enableSessionResumption &&
               enableZeroRtt == that.enableZeroRtt &&
               Objects.equals(tlsVersion, that.tlsVersion) &&
               Objects.equals(cipherSuites, that.cipherSuites) &&
               Objects.equals(applicationProtocols, that.applicationProtocols) &&
               Objects.equals(serverCertificates, that.serverCertificates) &&
               Objects.equals(serverPrivateKey, that.serverPrivateKey) &&
               Objects.equals(clientCertificates, that.clientCertificates) &&
               Objects.equals(clientPrivateKey, that.clientPrivateKey) &&
               Objects.equals(trustedCertificates, that.trustedCertificates) &&
               Objects.equals(sessionTimeout, that.sessionTimeout) &&
               Objects.equals(sessionTicketLifetime, that.sessionTicketLifetime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tlsVersion, cipherSuites, applicationProtocols,
                           serverCertificates, serverPrivateKey, clientCertificates,
                           clientPrivateKey, trustedCertificates, verifyPeer,
                           sessionTimeout, sessionTicketLifetime, enableSessionResumption, enableZeroRtt);
    }

    @Override
    public String toString() {
        return "QuicTlsConfiguration{" +
               "tlsVersion='" + tlsVersion + '\'' +
               ", cipherSuites=" + cipherSuites +
               ", applicationProtocols=" + applicationProtocols +
               ", serverCertificates=" + serverCertificates.size() + " certificates" +
               ", serverPrivateKey=[REDACTED]" +
               ", clientCertificates=" + clientCertificates.size() + " certificates" +
               ", clientPrivateKey=" + (clientPrivateKey != null ? "[REDACTED]" : "null") +
               ", trustedCertificates=" + trustedCertificates.size() + " certificates" +
               ", verifyPeer=" + verifyPeer +
               ", sessionTimeout=" + sessionTimeout +
               ", sessionTicketLifetime=" + sessionTicketLifetime +
               ", enableSessionResumption=" + enableSessionResumption +
               ", enableZeroRtt=" + enableZeroRtt +
               '}';
    }
}