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
     */
    QUIC
}