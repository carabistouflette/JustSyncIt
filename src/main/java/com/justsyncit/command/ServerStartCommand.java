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

package com.justsyncit.command;

import com.justsyncit.ServiceFactory;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

/**
 * Command for starting a backup server.
 * Follows Single Responsibility Principle by handling only server start
 * operations.
 */

public class ServerStartCommand implements Command {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServerStartCommand.class);

    private static final int DEFAULT_PORT = 8080;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int STARTUP_TIMEOUT_MS = 5000;

    private static final int STATUS_UPDATE_INTERVAL_MS = 1000;

    private final NetworkService networkService;
    private final ServiceFactory serviceFactory;
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    /**
     * Creates a server start command with dependency injection.
     *
     * @param networkService network service (may be null for lazy initialization)
     */
    public ServerStartCommand(NetworkService networkService) {
        this.networkService = networkService;
        this.serviceFactory = new ServiceFactory();
    }

    @Override
    public String getName() {
        return "server";
    }

    @Override
    public String getDescription() {
        return "Start a backup server to accept remote backup requests";
    }

    @Override
    public String getUsage() {
        return "server start [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Handle help option first
        if (args.length == 1 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        // Check for subcommand
        if (args.length == 0 || !args[0].equals("start")) {
            logger.error("Missing subcommand 'start'");
            System.err.println("Error: Missing subcommand 'start'");
            System.err.println(getUsage());
            System.err.println("Use 'help server start' for more information");
            return false;
        }

        StartOptions options;
        try {
            options = parseOptions(args);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid arguments: {}", e.getMessage());
            System.err.println(e.getMessage());
            return false;
        }

        if (options == null) {
            return false; // Help was displayed
        }

        NetworkService localService = null;

        try {
            NetworkService service = this.networkService;
            if (service == null && context != null) {
                service = context.getNetworkService();
            }
            if (service == null) {
                try {
                    localService = serviceFactory.createNetworkService();
                    service = localService;
                } catch (Exception e) {
                    logger.error("Failed to initialize network service", e);
                    System.err.println("Error: Failed to initialize network service: " + e.getMessage());
                    return false;
                }
            }

            return startServer(service, options);

        } catch (Exception e) {
            logger.error("Failed to start server", e);
            System.err.println("Error: Failed to start server: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(localService);
        }
    }

    /**
     * Parses command-line options.
     *
     * @param args command-line arguments
     * @return parsed options, or null if help was displayed
     * @throws IllegalArgumentException if invalid options are provided
     */
    private StartOptions parseOptions(String[] args) {
        int port = DEFAULT_PORT;
        TransportType transportType = TransportType.TCP;
        boolean daemon = false;
        boolean verbose = true;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[i + 1]);
                            if (port < MIN_PORT || port > MAX_PORT) {
                                throw new IllegalArgumentException(
                                        "Error: Port must be between " + MIN_PORT + " and " + MAX_PORT);
                            }
                            i++;
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Error: Invalid port number: " + args[i + 1]);
                        }
                    } else {
                        throw new IllegalArgumentException("Error: --port requires a value");
                    }
                    break;
                case "--transport":
                    if (i + 1 < args.length) {
                        try {
                            transportType = TransportType.valueOf(args[i + 1].toUpperCase(Locale.ROOT));
                            i++;
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException(
                                    "Error: Invalid transport type: " + args[i + 1] + ". Valid types: TCP, QUIC");
                        }
                    } else {
                        throw new IllegalArgumentException("Error: --transport requires a value (TCP|QUIC)");
                    }
                    break;
                case "--daemon":
                case "-d":
                    daemon = true;
                    break;
                case "--quiet":
                case "-q":
                    verbose = false;
                    break;
                case "--help":
                    displayHelp();
                    return null;
                default:
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Error: Unknown option: " + arg);
                    }
                    break;
            }
        }

        return new StartOptions(port, transportType, daemon, verbose);
    }

    /**
     * Starts the server with the given options.
     *
     * @param service network service
     * @param options start options
     * @return true if successful
     * @throws Exception if server fails to start
     */
    private boolean startServer(NetworkService service, StartOptions options) throws Exception {
        // Check if server is already running
        if (service.isServerRunning()) {
            logger.warn("Server is already running on port {}", service.getServerPort());
            System.err.println("Error: Server is already running on port " + service.getServerPort());
            return false;
        }

        if (options.verbose) {
            displayStartupInfo(options);
        }

        CompletableFuture<Void> startFuture = service.startServer(options.port, options.transportType);

        if (options.verbose) {
            System.out.println("Server starting...");
        }

        // Setup completion handler
        setupCompletionHandler(service, startFuture, options);

        // Wait for server to start with polling
        // Wait for server to start using Future.get() instead of polling/sleeping
        try {
            startFuture.get(STARTUP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("Server failed to start: Timed out after {}ms", STARTUP_TIMEOUT_MS);
            System.err.println("Server failed to start: Timed out after " + STARTUP_TIMEOUT_MS + "ms");
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            logger.error("Server start execution failed", e);
            System.err.println("Error: Server start failed: " + e.getCause().getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Server start interrupted");
            return false;
        }

        if (!service.isServerRunning()) {
            logger.error("Server failed to start (timeout or error)");
            System.err.println("Server failed to start (timeout or error)");
            return false;
        }

        if (options.daemon) {
            handleDaemonMode(options);
            return true;
        } else {
            handleInteractiveMode(service, options);
            return true;
        }
    }

    /**
     * Displays startup information.
     *
     * @param options start options
     */
    private void displayStartupInfo(StartOptions options) {
        System.out.println("Starting JustSyncIt backup server...");
        System.out.println("Port: " + options.port);
        System.out.println("Transport: " + options.transportType);
        System.out.println("Daemon mode: " + (options.daemon ? "enabled" : "disabled"));
        System.out.println();
    }

    /**
     * Sets up completion handler for server startup.
     *
     * @param service     network service
     * @param startFuture future for server start
     * @param options     start options
     */
    private void setupCompletionHandler(NetworkService service, CompletableFuture<Void> startFuture,
            StartOptions options) {
        startFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Failed to start server", throwable);
                System.err.println("Failed to start server: " + throwable.getMessage());
            } else if (options.verbose) {
                System.out.println("Server started successfully!");
                System.out.println("Listening on port " + options.port + " using " + options.transportType);
                if (!options.daemon) {
                    System.out.println("Press Ctrl+C to stop the server");
                    setupShutdownHook(service);
                }
            }
        });
    }

    /**
     * Sets up shutdown hook for graceful server shutdown.
     *
     * @param service network service
     */
    private void setupShutdownHook(NetworkService service) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("\nShutting down server...");
                service.stopServer().get();
                System.out.println("Server stopped.");
            } catch (Exception e) {
                logger.error("Error stopping server", e);
                System.err.println("Error stopping server: " + e.getMessage());
            } finally {
                stopLatch.countDown();
            }
        }));
    }

    /**
     * Handles daemon mode operation.
     *
     * @param options start options
     */
    private void handleDaemonMode(StartOptions options) {
        if (options.verbose) {
            System.out.println("Server started successfully in daemon mode.");
            System.out.println("Server is running in background on port " + options.port);
        }
    }

    /**
     * Handles interactive mode operation.
     *
     * @param service network service
     * @param options start options
     * @throws InterruptedException if interrupted while waiting
     */
    private void handleInteractiveMode(NetworkService service, StartOptions options) throws InterruptedException {
        if (options.verbose) {
            monitorServerWithStatus(service);
        } else {
            monitorServerQuiet(service);
        }
    }

    /**
     * Monitors server with periodic status updates.
     *
     * @param service network service
     * @throws InterruptedException if interrupted
     */
    private void monitorServerWithStatus(NetworkService service) throws InterruptedException {
        while (service.isServerRunning()) {
            // Wait for 1 second or until stop signal
            if (stopLatch.await(STATUS_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                break; // Stop signal received
            }

            NetworkService.NetworkStatistics stats = service.getStatistics();
            System.out.printf("\rConnections: %d, Transfers: %d, Bytes sent: %s, Bytes received: %s",
                    stats.getActiveConnections(),
                    stats.getCompletedTransfers(),
                    formatFileSize(stats.getTotalBytesSent()),
                    formatFileSize(stats.getTotalBytesReceived()));
            System.out.flush();
        }
    }

    /**
     * Monitors server in quiet mode.
     *
     * @param service network service
     * @throws InterruptedException if interrupted
     */
    private void monitorServerQuiet(NetworkService service) throws InterruptedException {
        // Wait until stop signal
        stopLatch.await();
    }

    /**
     * Safely closes a resource.
     *
     * @param resource resource to close
     */
    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                logger.warn("Failed to close resource", e);
                System.err.println("Warning: Failed to close resource: " + e.getMessage());
            }
        }
    }

    /**
     * Displays detailed help information for the server start command.
     */
    private void displayHelp() {
        System.out.println("Server Start Command Help");
        System.out.println("===========================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --port PORT           Port to listen on (default: " + DEFAULT_PORT + ")");
        System.out.println("  --transport TYPE       Transport protocol (TCP|QUIC, default: TCP)");
        System.out.println("  --daemon, -d          Run in daemon mode (background)");
        System.out.println("  --quiet, -q           Quiet mode with minimal output");
        System.out.println("  --help                Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  server start");
        System.out.println("  server start --port 9090");
        System.out.println("  server start --transport QUIC");
        System.out.println("  server start --port 9090 --transport QUIC --daemon");
        System.out.println("  server start --quiet");
    }

    /**
     * Formats file size in human-readable format.
     *
     * @param bytes the size in bytes
     * @return formatted size string
     */
    private String formatFileSize(long bytes) {
        final long KB = 1024;
        final long MB = KB * 1024;
        final long GB = MB * 1024;

        if (bytes < KB) {
            return bytes + " B";
        } else if (bytes < MB) {
            return formatWithDecimal(bytes / (double) KB) + " KB";
        } else if (bytes < GB) {
            return formatWithDecimal(bytes / (double) MB) + " MB";
        } else {
            return formatWithDecimal(bytes / (double) GB) + " GB";
        }
    }

    /**
     * Formats a number with one decimal place.
     *
     * @param value the value to format
     * @return formatted string
     */
    private String formatWithDecimal(double value) {
        return new java.text.DecimalFormat("#,##0.#").format(value);
    }

    /**
     * Simple data class to hold start options.
     */
    private static class StartOptions {
        final int port;
        final TransportType transportType;
        final boolean daemon;
        final boolean verbose;

        StartOptions(int port, TransportType transportType, boolean daemon, boolean verbose) {
            this.port = port;
            this.transportType = transportType;
            this.daemon = daemon;
            this.verbose = verbose;
        }
    }
}