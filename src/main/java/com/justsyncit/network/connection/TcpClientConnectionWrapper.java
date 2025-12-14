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

package com.justsyncit.network.connection;

import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.client.TcpClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for TCP client connections to implement the Connection interface.
 * Follows Adapter pattern to bridge TCP client with Connection interface.
 */
public class TcpClientConnectionWrapper implements Connection {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(TcpClientConnectionWrapper.class);

    /** The remote address of the connection. */
    private final InetSocketAddress remoteAddress;
    /** The TCP client for communication. */
    private final TcpClient tcpClient;
    /** Flag indicating if the connection is active. */
    private final AtomicBoolean active;
    /** Flag indicating if the connection is closed. */
    private final AtomicBoolean closed;
    /** The time when the connection was created. */
    private final Instant creationTime;
    /** The time of last activity on the connection. */
    private volatile Instant lastActivityTime;
    /** Counter for bytes sent. */
    private final AtomicLong bytesSent;
    /** Counter for bytes received. */
    private final AtomicLong bytesReceived;

    /**
     * Creates a new TCP client connection wrapper.
     *
     * @param remoteAddress remote address
     * @param tcpClient     TCP client
     * @return a new TcpClientConnectionWrapper instance
     * @throws IllegalArgumentException if remoteAddress or tcpClient is null
     */
    public static TcpClientConnectionWrapper create(InetSocketAddress remoteAddress, TcpClient tcpClient) {
        // Validate parameters before object creation
        if (remoteAddress == null) {
            throw new IllegalArgumentException("Remote address cannot be null");
        }
        if (tcpClient == null) {
            throw new IllegalArgumentException("TCP client cannot be null");
        }
        return new TcpClientConnectionWrapper(remoteAddress, tcpClient);
    }

    /**
     * Private constructor to enforce use of factory method.
     *
     * @param remoteAddress remote address (must not be null)
     * @param tcpClient     TCP client (must not be null)
     */
    private TcpClientConnectionWrapper(InetSocketAddress remoteAddress, TcpClient tcpClient) {
        this.remoteAddress = remoteAddress;
        // Store reference to TCP client - this is an injected dependency
        this.tcpClient = tcpClient;
        this.active = new AtomicBoolean(true);
        this.closed = new AtomicBoolean(false);
        this.creationTime = Instant.now();
        this.lastActivityTime = Instant.now();
        this.bytesSent = new AtomicLong(0);
        this.bytesReceived = new AtomicLong(0);
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return new InetSocketAddress("127.0.0.1", 0);
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.CLIENT;
    }

    @Override
    public boolean isActive() {
        // Check if TCP client has an active connection to this address
        if (closed.get() || tcpClient == null) {
            logger.debug("Connection inactive: closed={} tcpClient={}",
                    closed.get(), tcpClient == null);
            return false;
        }

        // Check if the connection is still in the active state
        if (!active.get()) {
            logger.debug("Connection inactive: active={}", active.get());
            return false;
        }

        // Check if the TCP client still has this connection
        try {
            boolean connected = tcpClient.isConnected(remoteAddress);
            logger.debug("Connection check for {}: connected={}", remoteAddress, connected);
            return connected;
        } catch (Exception e) {
            // If there's any exception checking connection, consider it inactive
            logger.debug("Connection inactive: exception={}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public CompletableFuture<Void> sendMessage(ProtocolMessage message) {
        if (!isActive()) {
            return CompletableFuture.failedFuture(new IOException("Connection is closed"));
        }

        updateLastActivityTime();
        bytesSent.addAndGet(message.getTotalSize());

        return tcpClient.sendMessage(message, remoteAddress);
    }

    @Override
    public CompletableFuture<Void> send(java.nio.ByteBuffer buffer) {
        if (!isActive()) {
            return CompletableFuture.failedFuture(new IOException("Connection is closed"));
        }
        updateLastActivityTime();
        return tcpClient.send(buffer, remoteAddress);
    }

    @Override
    public CompletableFuture<Void> sendFileRegion(java.nio.channels.FileChannel fileChannel, long position,
            long count) {
        if (!isActive()) {
            return CompletableFuture.failedFuture(new IOException("Connection is closed"));
        }
        updateLastActivityTime();
        return tcpClient.sendFileRegion(fileChannel, position, count, remoteAddress);
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        if (closed.compareAndSet(false, true)) {
            active.set(false);
            return tcpClient.disconnect(remoteAddress);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    public void close() {
        closeAsync().join();
    }

    @Override
    public Instant getCreationTime() {
        return creationTime;
    }

    @Override
    public Instant getLastActivityTime() {
        return lastActivityTime;
    }

    @Override
    public void updateLastActivityTime() {
        lastActivityTime = Instant.now();
    }

    @Override
    public long getBytesSent() {
        return bytesSent.get();
    }

    @Override
    public long getBytesReceived() {
        return bytesReceived.get();
    }
}