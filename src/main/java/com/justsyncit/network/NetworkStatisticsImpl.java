package com.justsyncit.network;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of NetworkStatistics.
 */
public class NetworkStatisticsImpl implements NetworkService.NetworkStatistics {

    /** Counter for total bytes sent. */
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    /** Counter for total bytes received. */
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    /** Counter for active connections. */
    private final AtomicLong activeConnections = new AtomicLong(0);
    /** Counter for completed transfers. */
    private final AtomicLong completedTransfers = new AtomicLong(0);
    /** Counter for failed transfers. */
    private final AtomicLong failedTransfers = new AtomicLong(0);
    /** Counter for messages sent. */
    private final AtomicLong messagesSent = new AtomicLong(0);
    /** Counter for messages received. */
    private final AtomicLong messagesReceived = new AtomicLong(0);
    /** Start time in milliseconds. */
    private volatile long startTime;
    /** End time in milliseconds. */
    private volatile long endTime;

    public void start() {
        startTime = System.currentTimeMillis();
        endTime = 0;
    }

    public void stop() {
        endTime = System.currentTimeMillis();
    }

    public void incrementBytesSent(long bytes) {
        totalBytesSent.addAndGet(bytes);
    }

    public void incrementBytesReceived(long bytes) {
        totalBytesReceived.addAndGet(bytes);
    }

    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    public void incrementCompletedTransfers() {
        completedTransfers.incrementAndGet();
    }

    public void incrementFailedTransfers() {
        failedTransfers.incrementAndGet();
    }

    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    public void incrementMessagesReceived() {
        messagesReceived.incrementAndGet();
    }

    @Override
    public long getTotalBytesSent() {
        return totalBytesSent.get();
    }

    @Override
    public long getTotalBytesReceived() {
        return totalBytesReceived.get();
    }

    @Override
    public long getTotalMessagesSent() {
        return messagesSent.get();
    }

    @Override
    public long getTotalMessagesReceived() {
        return messagesReceived.get();
    }

    @Override
    public int getActiveConnections() {
        return (int) activeConnections.get();
    }

    @Override
    public long getCompletedTransfers() {
        return completedTransfers.get();
    }

    @Override
    public long getFailedTransfers() {
        return failedTransfers.get();
    }

    @Override
    public double getAverageTransferRate() {
        long duration = (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        if (duration <= 0) {
            return 0;
        }
        return (double) (totalBytesSent.get() + totalBytesReceived.get()) / (duration / 1000.0);
    }

    @Override
    public long getUptimeMillis() {
        if (startTime == 0) {
            return 0;
        }
        return (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
    }
}
