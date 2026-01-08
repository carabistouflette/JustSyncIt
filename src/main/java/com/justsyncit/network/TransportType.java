package com.justsyncit.network;

/**
 * Enumeration of supported transport types for network communication.
 */
public enum TransportType {
    /**
     * TCP transport protocol - traditional reliable connection-oriented protocol.
     */
    TCP,

    /**
     * QUIC transport protocol - modern UDP-based transport with TLS 1.3 built-in.
     * 
     * @deprecated QUIC transport is deprecated and will be removed in a future
     *             release.
     *             All QUIC requests automatically fall back to TCP. Use
     *             {@link #TCP} instead.
     */
    @Deprecated(since = "0.2.0", forRemoval = true)
    QUIC
}