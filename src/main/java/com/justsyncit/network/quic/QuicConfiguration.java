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

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration class for QUIC transport settings.
 * Provides comprehensive configuration options for QUIC connections including
 * performance tuning, security settings, and connection management.
 */
public class QuicConfiguration {

    /** Default maximum idle timeout for QUIC connections. */
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofSeconds(30);

    /** Default maximum number of bidirectional streams. */
    public static final int DEFAULT_MAX_BIDIRECTIONAL_STREAMS = 100;

    /** Default maximum number of unidirectional streams. */
    public static final int DEFAULT_MAX_UNIDIRECTIONAL_STREAMS = 100;

    /** Default initial maximum data for connections. */
    public static final long DEFAULT_INITIAL_MAX_DATA = 10 * 1024 * 1024; // 10MB

    /** Default initial maximum stream data. */
    public static final long DEFAULT_INITIAL_MAX_STREAM_DATA = 1024 * 1024; // 1MB

    /** Default maximum UDP payload size. */
    public static final int DEFAULT_MAX_UDP_PAYLOAD_SIZE = 1200;

    /** Default connection migration support. */
    public static final boolean DEFAULT_CONNECTION_MIGRATION = false;

    /** Default 0-RTT support. */
    public static final boolean DEFAULT_0_RTT_SUPPORT = true;

    /** Maximum idle timeout for connections. */
    private final Duration idleTimeout;

    /** Maximum number of bidirectional streams per connection. */
    private final int maxBidirectionalStreams;

    /** Maximum number of unidirectional streams per connection. */
    private final int maxUnidirectionalStreams;

    /** Initial maximum data that can be sent on a connection. */
    private final long initialMaxData;

    /** Initial maximum data that can be sent on a stream. */
    private final long initialMaxStreamData;

    /** Maximum UDP payload size. */
    private final int maxUdpPayloadSize;

    /** Whether connection migration is supported. */
    private final boolean connectionMigration;

    /** Whether 0-RTT data is supported. */
    private final boolean zeroRttSupport;

    /** TLS configuration. */
    private final QuicTlsConfiguration tlsConfiguration;

    private QuicConfiguration(Builder builder) {
        this.idleTimeout = Objects.requireNonNull(builder.idleTimeout, "idleTimeout cannot be null");
        this.maxBidirectionalStreams = builder.maxBidirectionalStreams;
        this.maxUnidirectionalStreams = builder.maxUnidirectionalStreams;
        this.initialMaxData = builder.initialMaxData;
        this.initialMaxStreamData = builder.initialMaxStreamData;
        this.maxUdpPayloadSize = builder.maxUdpPayloadSize;
        this.connectionMigration = builder.connectionMigration;
        this.zeroRttSupport = builder.zeroRttSupport;
        this.tlsConfiguration = Objects.requireNonNull(builder.tlsConfiguration, "tlsConfiguration cannot be null");
    }

    /**
     * Gets the maximum idle timeout.
     *
     * @return the idle timeout
     */
    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * Gets the maximum number of bidirectional streams.
     *
     * @return the maximum bidirectional streams
     */
    public int getMaxBidirectionalStreams() {
        return maxBidirectionalStreams;
    }

    /**
     * Gets the maximum number of unidirectional streams.
     *
     * @return the maximum unidirectional streams
     */
    public int getMaxUnidirectionalStreams() {
        return maxUnidirectionalStreams;
    }

    /**
     * Gets the initial maximum data for connections.
     *
     * @return the initial maximum data
     */
    public long getInitialMaxData() {
        return initialMaxData;
    }

    /**
     * Gets the initial maximum stream data.
     *
     * @return the initial maximum stream data
     */
    public long getInitialMaxStreamData() {
        return initialMaxStreamData;
    }

    /**
     * Gets the maximum UDP payload size.
     *
     * @return the maximum UDP payload size
     */
    public int getMaxUdpPayloadSize() {
        return maxUdpPayloadSize;
    }

    /**
     * Checks if connection migration is supported.
     *
     * @return true if connection migration is supported
     */
    public boolean isConnectionMigration() {
        return connectionMigration;
    }

    /**
     * Checks if 0-RTT is supported.
     *
     * @return true if 0-RTT is supported
     */
    public boolean isZeroRttSupport() {
        return zeroRttSupport;
    }

    /**
     * Gets the TLS configuration.
     *
     * @return the TLS configuration
     */
    public QuicTlsConfiguration getTlsConfiguration() {
        return tlsConfiguration;
    }

    /**
     * Creates a new builder for QUIC configuration.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default QUIC configuration.
     *
     * @return a default configuration
     */
    public static QuicConfiguration defaultConfiguration() {
        return builder().build();
    }

    /**
     * Builder for QuicConfiguration.
     */
    public static class Builder {
        /** Idle timeout. */
        private Duration idleTimeout = DEFAULT_IDLE_TIMEOUT;
        /** Maximum bidirectional streams. */
        private int maxBidirectionalStreams = DEFAULT_MAX_BIDIRECTIONAL_STREAMS;
        /** Maximum unidirectional streams. */
        private int maxUnidirectionalStreams = DEFAULT_MAX_UNIDIRECTIONAL_STREAMS;
        /** Initial maximum data. */
        private long initialMaxData = DEFAULT_INITIAL_MAX_DATA;
        /** Initial maximum stream data. */
        private long initialMaxStreamData = DEFAULT_INITIAL_MAX_STREAM_DATA;
        /** Maximum UDP payload size. */
        private int maxUdpPayloadSize = DEFAULT_MAX_UDP_PAYLOAD_SIZE;
        /** Connection migration flag. */
        private boolean connectionMigration = DEFAULT_CONNECTION_MIGRATION;
        /** Zero RTT support flag. */
        private boolean zeroRttSupport = DEFAULT_0_RTT_SUPPORT;
        /** TLS configuration. */
        private QuicTlsConfiguration tlsConfiguration = QuicTlsConfiguration.defaultConfiguration();

        /**
         * Sets the idle timeout.
         *
         * @param idleTimeout the idle timeout
         * @return this builder
         */
        public Builder idleTimeout(Duration idleTimeout) {
            this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout cannot be null");
            if (this.idleTimeout.isNegative() || this.idleTimeout.isZero()) {
                throw new IllegalArgumentException("idleTimeout must be positive");
            }
            return this;
        }

        /**
         * Sets the maximum bidirectional streams.
         *
         * @param maxBidirectionalStreams the maximum bidirectional streams
         * @return this builder
         */
        public Builder maxBidirectionalStreams(int maxBidirectionalStreams) {
            if (maxBidirectionalStreams <= 0) {
                throw new IllegalArgumentException("maxBidirectionalStreams must be positive");
            }
            this.maxBidirectionalStreams = maxBidirectionalStreams;
            return this;
        }

        /**
         * Sets the maximum unidirectional streams.
         *
         * @param maxUnidirectionalStreams the maximum unidirectional streams
         * @return this builder
         */
        public Builder maxUnidirectionalStreams(int maxUnidirectionalStreams) {
            if (maxUnidirectionalStreams <= 0) {
                throw new IllegalArgumentException("maxUnidirectionalStreams must be positive");
            }
            this.maxUnidirectionalStreams = maxUnidirectionalStreams;
            return this;
        }

        /**
         * Sets the initial maximum data.
         *
         * @param initialMaxData the initial maximum data
         * @return this builder
         */
        public Builder initialMaxData(long initialMaxData) {
            if (initialMaxData <= 0) {
                throw new IllegalArgumentException("initialMaxData must be positive");
            }
            this.initialMaxData = initialMaxData;
            return this;
        }

        /**
         * Sets the initial maximum stream data.
         *
         * @param initialMaxStreamData the initial maximum stream data
         * @return this builder
         */
        public Builder initialMaxStreamData(long initialMaxStreamData) {
            if (initialMaxStreamData <= 0) {
                throw new IllegalArgumentException("initialMaxStreamData must be positive");
            }
            this.initialMaxStreamData = initialMaxStreamData;
            return this;
        }

        /**
         * Sets the maximum UDP payload size.
         *
         * @param maxUdpPayloadSize the maximum UDP payload size
         * @return this builder
         */
        public Builder maxUdpPayloadSize(int maxUdpPayloadSize) {
            if (maxUdpPayloadSize < 1200) {
                throw new IllegalArgumentException("maxUdpPayloadSize must be at least 1200 bytes");
            }
            this.maxUdpPayloadSize = maxUdpPayloadSize;
            return this;
        }

        /**
         * Enables or disables connection migration.
         *
         * @param connectionMigration true to enable connection migration
         * @return this builder
         */
        public Builder connectionMigration(boolean connectionMigration) {
            this.connectionMigration = connectionMigration;
            return this;
        }

        /**
         * Enables or disables 0-RTT support.
         *
         * @param zeroRttSupport true to enable 0-RTT support
         * @return this builder
         */
        public Builder zeroRttSupport(boolean zeroRttSupport) {
            this.zeroRttSupport = zeroRttSupport;
            return this;
        }

        /**
         * Sets the TLS configuration.
         *
         * @param tlsConfiguration the TLS configuration
         * @return this builder
         */
        public Builder tlsConfiguration(QuicTlsConfiguration tlsConfiguration) {
            this.tlsConfiguration = Objects.requireNonNull(tlsConfiguration, "tlsConfiguration cannot be null");
            return this;
        }

        /**
         * Builds the QUIC configuration.
         *
         * @return the configuration
         */
        public QuicConfiguration build() {
            return new QuicConfiguration(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuicConfiguration that = (QuicConfiguration) o;
        return maxBidirectionalStreams == that.maxBidirectionalStreams
                && maxUnidirectionalStreams == that.maxUnidirectionalStreams
                && initialMaxData == that.initialMaxData
                && initialMaxStreamData == that.initialMaxStreamData
                && maxUdpPayloadSize == that.maxUdpPayloadSize
                && connectionMigration == that.connectionMigration
                && zeroRttSupport == that.zeroRttSupport
                && Objects.equals(idleTimeout, that.idleTimeout)
                && Objects.equals(tlsConfiguration, that.tlsConfiguration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idleTimeout, maxBidirectionalStreams, maxUnidirectionalStreams,
                           initialMaxData, initialMaxStreamData, maxUdpPayloadSize,
                           connectionMigration, zeroRttSupport, tlsConfiguration);
    }

    @Override
    public String toString() {
        return "QuicConfiguration{"
                + "idleTimeout=" + idleTimeout
                + ", maxBidirectionalStreams=" + maxBidirectionalStreams
                + ", maxUnidirectionalStreams=" + maxUnidirectionalStreams
                + ", initialMaxData=" + initialMaxData
                + ", initialMaxStreamData=" + initialMaxStreamData
                + ", maxUdpPayloadSize=" + maxUdpPayloadSize
                + ", connectionMigration=" + connectionMigration
                + ", zeroRttSupport=" + zeroRttSupport
                + ", tlsConfiguration=" + tlsConfiguration
                + '}';
    }
}