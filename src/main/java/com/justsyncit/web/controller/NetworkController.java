package com.justsyncit.web.controller;

import com.justsyncit.network.NetworkService;
import com.justsyncit.web.WebServerContext;
import com.justsyncit.web.dto.ApiError;
import io.javalin.http.Context;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for network operations and statistics.
 */
public class NetworkController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkController.class);
    private final WebServerContext context;

    public NetworkController(WebServerContext context) {
        this.context = context;
    }

    /**
     * GET /api/network/stats - Get overall network statistics.
     */
    public void getNetworkStats(Context ctx) {
        try {
            NetworkService networkService = context.getNetworkService();
            if (networkService == null) {
                ctx.status(503)
                        .json(ApiError.of(530, "Service Unavailable", "Network service not initialized", ctx.path()));
                return;
            }

            NetworkService.NetworkStatistics stats = networkService.getStatistics();
            if (stats == null) {
                ctx.status(200).json(Map.of(
                        "activeConnections", 0,
                        "bytesSent", 0,
                        "bytesReceived", 0,
                        "messagesSent", 0,
                        "messagesReceived", 0,
                        "uptime", 0));
                return;
            }

            ctx.json(Map.of(
                    "activeConnections", stats.getActiveConnections(),
                    "bytesSent", stats.getTotalBytesSent(),
                    "bytesReceived", stats.getTotalBytesReceived(),
                    "messagesSent", stats.getTotalMessagesSent(),
                    "messagesReceived", stats.getTotalMessagesReceived(),
                    "completedTransfers", stats.getCompletedTransfers(),
                    "failedTransfers", stats.getFailedTransfers(),
                    "averageRate", stats.getAverageTransferRate(),
                    "uptime", stats.getUptimeMillis()));
        } catch (Exception e) {
            LOGGER.error("Failed to get network stats", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }

    /**
     * GET /api/network/status - Get current network service status.
     */
    public void getNetworkStatus(Context ctx) {
        try {
            NetworkService networkService = context.getNetworkService();
            if (networkService == null) {
                ctx.json(Map.of("online", false, "serverRunning", false));
                return;
            }

            ctx.json(Map.of(
                    "online", networkService.isRunning(),
                    "serverRunning", networkService.isServerRunning(),
                    "port", networkService.getServerPort(),
                    "defaultTransport", String.valueOf(networkService.getDefaultTransportType())));
        } catch (Exception e) {
            LOGGER.error("Failed to get network status", e);
            ctx.status(500).json(ApiError.internalError(e.getMessage(), ctx.path()));
        }
    }
}
