package com.justsyncit.network;

/**
 * Configuration for network operations.
 * Holds parameters for TCP/IP tuning and connection management.
 */
public class NetworkConfiguration {

    // Default values tuned for high-bandwidth WAN connections
    public static final int DEFAULT_SEND_BUFFER_SIZE = 4 * 1024 * 1024; // 4MB
    public static final int DEFAULT_RECEIVE_BUFFER_SIZE = 4 * 1024 * 1024; // 4MB
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    public static final boolean DEFAULT_TCP_NO_DELAY = true;
    public static final boolean DEFAULT_KEEP_ALIVE = true;
    public static final boolean DEFAULT_REUSE_ADDRESS = true;

    private final int sendBufferSize;
    private final int receiveBufferSize;
    private final int connectTimeoutMs;
    private final boolean tcpNoDelay;
    private final boolean keepAlive;
    private final boolean reuseAddress;

    /**
     * Creates a new NetworkConfiguration with default values.
     */
    public NetworkConfiguration() {
        this(DEFAULT_SEND_BUFFER_SIZE, DEFAULT_RECEIVE_BUFFER_SIZE, DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_TCP_NO_DELAY, DEFAULT_KEEP_ALIVE, DEFAULT_REUSE_ADDRESS);
    }

    /**
     * Creates a new NetworkConfiguration with specified values.
     *
     * @param sendBufferSize    SO_SNDBUF size in bytes
     * @param receiveBufferSize SO_RCVBUF size in bytes
     * @param connectTimeoutMs  connection timeout in milliseconds
     * @param tcpNoDelay        TCP_NODELAY setting
     * @param keepAlive         SO_KEEPALIVE setting
     * @param reuseAddress      SO_REUSEADDR setting
     */
    public NetworkConfiguration(int sendBufferSize, int receiveBufferSize, int connectTimeoutMs,
            boolean tcpNoDelay, boolean keepAlive, boolean reuseAddress) {
        this.sendBufferSize = sendBufferSize;
        this.receiveBufferSize = receiveBufferSize;
        this.connectTimeoutMs = connectTimeoutMs;
        this.tcpNoDelay = tcpNoDelay;
        this.keepAlive = keepAlive;
        this.reuseAddress = reuseAddress;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }
}
