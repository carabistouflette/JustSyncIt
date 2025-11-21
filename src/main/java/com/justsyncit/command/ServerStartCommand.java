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

import com.justsyncit.ServiceException;
import com.justsyncit.ServiceFactory;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.TransportType;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Command for starting a backup server.
 * Follows Single Responsibility Principle by handling only server start operations.
 */
public class ServerStartCommand implements Command {

    private final NetworkService networkService;
    private final ServiceFactory serviceFactory;

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
            System.err.println("Error: Missing subcommand 'start'");
            System.err.println(getUsage());
            System.err.println("Use 'help server start' for more information");
            return false;
        }

        // Parse options
        final int port;
        final TransportType transportType;
        final boolean daemon;
        final boolean verbose;

        // Set defaults
        int defaultPort = 8080;
        TransportType defaultTransportType = TransportType.TCP;
        boolean defaultDaemon = false;
        boolean defaultVerbose = true;

        // Parse options
        int currentPort = defaultPort;
        TransportType currentTransportType = defaultTransportType;
        boolean currentDaemon = defaultDaemon;
        boolean currentVerbose = defaultVerbose;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            currentPort = Integer.parseInt(args[i + 1]);
                            if (currentPort < 1 || currentPort > 65535) {
                                System.err.println("Error: Port must be between 1 and 65535");
                                return false;
                            }
                            i++; // Skip the next argument
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Invalid port number: " + args[i + 1]);
                            return false;
                        }
                    } else {
                        System.err.println("Error: --port requires a value");
                        return false;
                    }
                    break;
                case "--transport":
                    if (i + 1 < args.length) {
                        try {
                            currentTransportType = TransportType.valueOf(args[i + 1].toUpperCase());
                            i++; // Skip the next argument
                        } catch (IllegalArgumentException e) {
                            System.err.println("Error: Invalid transport type: " + args[i + 1] + ". Valid types: TCP, QUIC");
                            return false;
                        }
                    } else {
                        System.err.println("Error: --transport requires a value (TCP|QUIC)");
                        return false;
                    }
                    break;
                case "--daemon":
                case "-d":
                    currentDaemon = true;
                    break;
                case "--quiet":
                case "-q":
                    currentVerbose = false;
                    break;
                case "--help":
                    displayHelp();
                    return true;
                default:
                    if (arg.startsWith("--")) {
                        System.err.println("Error: Unknown option: " + arg);
                        return false;
                    }
                    break;
            }
        }

        // Assign final values
        port = currentPort;
        transportType = currentTransportType;
        daemon = currentDaemon;
        verbose = currentVerbose;

        // Create service if not provided
        final NetworkService service;
        if (networkService == null) {
            try {
                service = serviceFactory.createNetworkService();
            } catch (Exception e) {
                System.err.println("Error: Failed to initialize network service: " + e.getMessage());
                return false;
            }
        } else {
            service = networkService;
        }

        try {
            // Check if server is already running
            if (service.isServerRunning()) {
                System.err.println("Error: Server is already running on port " + service.getServerPort());
                return false;
            }

            // Start the server
            if (verbose) {
                System.out.println("Starting JustSyncIt backup server...");
                System.out.println("Port: " + port);
                System.out.println("Transport: " + transportType);
                System.out.println("Daemon mode: " + (daemon ? "enabled" : "disabled"));
                System.out.println();
            }

            CompletableFuture<Void> startFuture = service.startServer(port, transportType);
            
            if (verbose) {
                System.out.println("Server starting...");
                
                // Wait for server to start
                startFuture.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        System.err.println("Failed to start server: " + throwable.getMessage());
                        if (!daemon) {
                            System.exit(1);
                        }
                    } else {
                        System.out.println("Server started successfully!");
                        System.out.println("Listening on port " + port + " using " + transportType);
                        System.out.println("Press Ctrl+C to stop the server");
                        
                        if (!daemon) {
                            // Add shutdown hook
                            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                try {
                                    System.out.println("\nShutting down server...");
                                    service.stopServer().get();
                                    System.out.println("Server stopped.");
                                } catch (Exception e) {
                                    System.err.println("Error stopping server: " + e.getMessage());
                                }
                            }));
                            
                            // Keep the server running
                            try {
                                Thread.currentThread().join();
                            } catch (InterruptedException e) {
                                // Thread interrupted, shutdown
                            }
                        }
                    }
                });
                
                // Wait a bit to see if server starts immediately
                Thread.sleep(1000);
                
                if (service.isServerRunning()) {
                    if (daemon) {
                        System.out.println("Server started successfully in daemon mode.");
                        System.out.println("Server is running in background on port " + port);
                        return true;
                    } else {
                        // Keep main thread alive for interactive mode
                        while (service.isServerRunning()) {
                            try {
                                Thread.sleep(1000);
                                
                                // Show status periodically
                                NetworkService.NetworkStatistics stats = service.getStatistics();
                                System.out.printf("\rConnections: %d, Transfers: %d, Bytes sent: %s, Bytes received: %s",
                            stats.getActiveConnections(),
                            stats.getCompletedTransfers(),
                            formatFileSize(stats.getTotalBytesSent()),
                            formatFileSize(stats.getTotalBytesReceived()));
                                System.out.flush();
                                
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
                } else {
                    System.err.println("Server failed to start");
                    return false;
                }
            } else {
                // Quiet mode - just start and exit
                startFuture.get();
                if (daemon) {
                    return true;
                } else {
                    // In quiet mode without daemon, we still need to keep the server running
                    while (service.isServerRunning()) {
                        Thread.sleep(1000);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error: Failed to start server: " + e.getMessage());
            return false;
        } finally {
            // Clean up resources if we created them
            if (networkService == null && service != null && !service.isServerRunning()) {
                try {
                    service.close();
                } catch (Exception e) {
                    System.err.println("Warning: Failed to close network service: " + e.getMessage());
                }
            }
        }

        return true;
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
        System.out.println("  --port PORT           Port to listen on (default: 8080)");
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
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return new java.text.DecimalFormat("#,##0.#").format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return new java.text.DecimalFormat("#,##0.#").format(bytes / (1024.0 * 1024)) + " MB";
        } else {
            return new java.text.DecimalFormat("#,##0.#").format(bytes / (1024.0 * 1024 * 1024)) + " GB";
        }
    }
}