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

package com.justsyncit.integration.util;

import com.justsyncit.ServiceException;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;
import com.justsyncit.network.protocol.ProtocolMessage;
import com.justsyncit.network.transfer.FileTransferResult;
import com.justsyncit.storage.ContentStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for simulating network conditions and testing network
 * resilience.
 * Provides methods to simulate various network scenarios for E2E testing.
 */
public class NetworkSimulationUtil {

    /**
     * Network condition simulator that wraps a real NetworkService.
     */
    public static class NetworkConditionSimulator implements NetworkService {

        private final NetworkService delegate;
        private final AtomicBoolean simulateLatency = new AtomicBoolean(false);
        private final AtomicBoolean simulatePacketLoss = new AtomicBoolean(false);
        private final AtomicBoolean simulateConnectionFailure = new AtomicBoolean(false);
        private final AtomicInteger latencyMs = new AtomicInteger(100);
        private final AtomicInteger packetLossPercentage = new AtomicInteger(10);
        private final AtomicInteger failureRatePercentage = new AtomicInteger(5);
        private final AtomicInteger connectionCount = new AtomicInteger(0);
        private final AtomicLong bytesTransferred = new AtomicLong(0);
        private final AtomicLong messagesTransferred = new AtomicLong(0);

        public NetworkConditionSimulator(NetworkService delegate) {
            this.delegate = delegate;
        }

        public void enableLatencySimulation(int latencyMs) {
            this.simulateLatency.set(true);
            this.latencyMs.set(latencyMs);
        }

        public void disableLatencySimulation() {
            this.simulateLatency.set(false);
        }

        public void enablePacketLossSimulation(int percentage) {
            this.simulatePacketLoss.set(true);
            this.packetLossPercentage.set(Math.min(100, Math.max(0, percentage)));
        }

        public void disablePacketLossSimulation() {
            this.simulatePacketLoss.set(false);
        }

        public void enableConnectionFailureSimulation(int percentage) {
            this.simulateConnectionFailure.set(true);
            this.failureRatePercentage.set(Math.min(100, Math.max(0, percentage)));
        }

        public void disableConnectionFailureSimulation() {
            this.simulateConnectionFailure.set(false);
        }

        private void simulateLatency() {
            if (simulateLatency.get()) {
                try {
                    Thread.sleep(latencyMs.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private boolean shouldSimulatePacketLoss() {
            if (simulatePacketLoss.get()) {
                return Math.random() * 100 < packetLossPercentage.get();
            }
            return false;
        }

        private boolean shouldSimulateConnectionFailure() {
            if (simulateConnectionFailure.get()) {
                return Math.random() * 100 < failureRatePercentage.get();
            }
            return false;
        }

        private <T> CompletableFuture<T> simulate(java.util.function.Supplier<CompletableFuture<T>> action,
                boolean checkConnectionFailure, boolean checkPacketLoss) {
            if (checkConnectionFailure && shouldSimulateConnectionFailure()) {
                return CompletableFuture.failedFuture(new IOException("Simulated connection failure"));
            }
            if (checkPacketLoss && shouldSimulatePacketLoss()) {
                return CompletableFuture.failedFuture(new IOException("Simulated packet loss"));
            }
            simulateLatency();
            return action.get();
        }

        @Override
        public CompletableFuture<Void> startServer(int port) throws IOException, ServiceException {
            return simulate(() -> {
                try {
                    return delegate.startServer(port);
                } catch (IOException | ServiceException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, true, false);
        }

        @Override
        public CompletableFuture<Void> startServer(int port, TransportType transportType)
                throws IOException, ServiceException {
            return simulate(() -> {
                try {
                    return delegate.startServer(port, transportType);
                } catch (IOException | ServiceException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, true, false);
        }

        @Override
        public CompletableFuture<Void> stopServer() {
            simulateLatency();
            return delegate.stopServer();
        }

        @Override
        public CompletableFuture<Void> connectToNode(InetSocketAddress address) throws IOException {
            return simulate(() -> {
                try {
                    connectionCount.incrementAndGet();
                    return delegate.connectToNode(address);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, true, false);
        }

        @Override
        public CompletableFuture<Void> connectToNode(InetSocketAddress address, TransportType transportType)
                throws IOException {
            return simulate(() -> {
                try {
                    connectionCount.incrementAndGet();
                    return delegate.connectToNode(address, transportType);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, true, false);
        }

        @Override
        public CompletableFuture<Void> disconnectFromNode(InetSocketAddress address) {
            simulateLatency();
            return delegate.disconnectFromNode(address);
        }

        @Override
        public CompletableFuture<FileTransferResult> sendFile(Path filePath, InetSocketAddress remoteAddress,
                ContentStore contentStore) throws IOException {
            return simulate(() -> {
                try {
                    bytesTransferred.addAndGet(Files.size(filePath));
                    return delegate.sendFile(filePath, remoteAddress, contentStore);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, false, true);
        }

        @Override
        public CompletableFuture<FileTransferResult> sendFile(Path filePath, InetSocketAddress remoteAddress,
                ContentStore contentStore, TransportType transportType) throws IOException {
            return simulate(() -> {
                try {
                    bytesTransferred.addAndGet(Files.size(filePath));
                    return delegate.sendFile(filePath, remoteAddress, contentStore, transportType);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, false, true);
        }

        @Override
        public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress remoteAddress)
                throws IOException {
            return simulate(() -> {
                try {
                    messagesTransferred.incrementAndGet();
                    return delegate.sendMessage(message, remoteAddress);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, false, true);
        }

        @Override
        public CompletableFuture<Void> sendFilePart(Path filePath, long offset, long length,
                InetSocketAddress remoteAddress)
                throws IOException {
            return simulate(() -> {
                try {
                    bytesTransferred.addAndGet(length);
                    return delegate.sendFilePart(filePath, offset, length, remoteAddress);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, false, true);
        }

        @Override
        public CompletableFuture<Void> sendFilePart(Path filePath, long offset, long length,
                InetSocketAddress remoteAddress, TransportType transportType) throws IOException {
            return simulate(() -> {
                try {
                    bytesTransferred.addAndGet(length);
                    return delegate.sendFilePart(filePath, offset, length, remoteAddress, transportType);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, false, true);
        }

        @Override
        public CompletableFuture<Void> sendMessage(ProtocolMessage message, InetSocketAddress remoteAddress,
                TransportType transportType) throws IOException {
            return simulate(() -> {
                try {
                    messagesTransferred.incrementAndGet();
                    return delegate.sendMessage(message, remoteAddress, transportType);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, false, true);
        }

        @Override
        public void addNetworkEventListener(NetworkEventListener listener) {
            delegate.addNetworkEventListener(listener);
        }

        @Override
        public void removeNetworkEventListener(NetworkEventListener listener) {
            delegate.removeNetworkEventListener(listener);
        }

        @Override
        public boolean isRunning() {
            return delegate.isRunning();
        }

        @Override
        public boolean isServerRunning() {
            return delegate.isServerRunning();
        }

        @Override
        public int getServerPort() {
            return delegate.getServerPort();
        }

        @Override
        public int getActiveConnectionCount() {
            return delegate.getActiveConnectionCount();
        }

        @Override
        public int getActiveTransferCount() {
            return delegate.getActiveTransferCount();
        }

        @Override
        public long getBytesSent() {
            return bytesTransferred.get() + delegate.getBytesSent();
        }

        @Override
        public long getBytesReceived() {
            return delegate.getBytesReceived();
        }

        @Override
        public long getMessagesSent() {
            return messagesTransferred.get() + delegate.getMessagesSent();
        }

        @Override
        public long getMessagesReceived() {
            return delegate.getMessagesReceived();
        }

        @Override
        public NetworkStatistics getStatistics() {
            return delegate.getStatistics();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public TransportType getConnectionTransportType(InetSocketAddress remoteAddress) {
            return delegate.getConnectionTransportType(remoteAddress);
        }

        @Override
        public TransportType getDefaultTransportType() {
            return delegate.getDefaultTransportType();
        }

        @Override
        public void setDefaultTransportType(TransportType transportType) {
            delegate.setDefaultTransportType(transportType);
        }

        // Additional methods for test validation
        public int getConnectionAttempts() {
            return connectionCount.get();
        }

        public long getSimulatedBytesTransferred() {
            return bytesTransferred.get();
        }

        public long getSimulatedMessagesTransferred() {
            return messagesTransferred.get();
        }
    }

    /**
     * Creates a network service with simulated poor network conditions.
     */
    public static NetworkConditionSimulator createPoorNetworkSimulator(NetworkService delegate) {
        NetworkConditionSimulator simulator = new NetworkConditionSimulator(delegate);
        simulator.enableLatencySimulation(500); // 500ms latency
        simulator.enablePacketLossSimulation(15); // 15% packet loss
        simulator.enableConnectionFailureSimulation(10); // 10% connection failure rate
        return simulator;
    }

    /**
     * Creates a network service with simulated excellent network conditions.
     */
    public static NetworkConditionSimulator createExcellentNetworkSimulator(NetworkService delegate) {
        NetworkConditionSimulator simulator = new NetworkConditionSimulator(delegate);
        simulator.enableLatencySimulation(10); // 10ms latency
        simulator.enablePacketLossSimulation(0); // 0% packet loss
        simulator.enableConnectionFailureSimulation(0); // 0% connection failure rate
        return simulator;
    }

    /**
     * Creates a network service with simulated intermittent connectivity.
     */
    public static NetworkConditionSimulator createIntermittentNetworkSimulator(NetworkService delegate) {
        NetworkConditionSimulator simulator = new NetworkConditionSimulator(delegate);
        simulator.enableLatencySimulation(200); // 200ms latency
        simulator.enablePacketLossSimulation(5); // 5% packet loss
        simulator.enableConnectionFailureSimulation(25); // 25% connection failure rate
        return simulator;
    }

    /**
     * Finds an available port for testing.
     */
    public static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 8080; // fallback port
        }
    }

    /**
     * Waits for a server to be ready on the specified port.
     */
    public static boolean waitForServerReady(String host, int port, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                java.net.Socket socket = new java.net.Socket(host, port);
                socket.close();
                return true;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Validates network connectivity between two points.
     */
    public static boolean validateConnectivity(String host, int port) {
        try {
            java.net.Socket socket = new java.net.Socket(host, port);
            socket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}