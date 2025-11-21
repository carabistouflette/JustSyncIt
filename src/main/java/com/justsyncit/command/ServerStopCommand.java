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

import java.io.IOException;

/**
 * Command for stopping a backup server.
 * Follows Single Responsibility Principle by handling only server stop operations.
 */
public class ServerStopCommand implements Command {

    private final NetworkService networkService;
    private final ServiceFactory serviceFactory;

    /**
     * Creates a server stop command with dependency injection.
     *
     * @param networkService network service (may be null for lazy initialization)
     */
    public ServerStopCommand(NetworkService networkService) {
        this.networkService = networkService;
        this.serviceFactory = new ServiceFactory();
    }

    @Override
    public String getName() {
        return "server";
    }

    @Override
    public String getDescription() {
        return "Stop a running backup server";
    }

    @Override
    public String getUsage() {
        return "server stop [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Handle help option first
        if (args.length == 1 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        // Check for subcommand
        if (args.length == 0 || !args[0].equals("stop")) {
            System.err.println("Error: Missing subcommand 'stop'");
            System.err.println(getUsage());
            System.err.println("Use 'help server stop' for more information");
            return false;
        }

        // Parse options
        boolean force = false;
        boolean verbose = true;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--force":
                force = true;
                    break;
                case "--quiet":
                case "-q":
                    verbose = false;
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
            // Check if server is running
            if (!service.isServerRunning()) {
                System.err.println("Error: Server is not running");
                return false;
            }

            // Show server status before stopping
            if (verbose) {
                System.out.println("Server Status Before Stop:");
                System.out.println("=========================");
                displayServerStatus(service);
                System.out.println();
            }

            // Stop the server
            if (verbose) {
                System.out.println("Stopping server...");
            }

            service.stopServer().get();

            if (verbose) {
                System.out.println("Server stopped successfully!");
                
                // Show final statistics
                NetworkService.NetworkStatistics stats = service.getStatistics();
                System.out.println();
                System.out.println("Final Statistics:");
                System.out.println("=================");
                System.out.println("Total bytes sent: " + formatFileSize(stats.getTotalBytesSent()));
                System.out.println("Total bytes received: " + formatFileSize(stats.getTotalBytesReceived()));
                System.out.println("Total messages sent: " + stats.getTotalMessagesSent());
                System.out.println("Total messages received: " + stats.getTotalMessagesReceived());
                System.out.println("Completed transfers: " + stats.getCompletedTransfers());
                System.out.println("Failed transfers: " + stats.getFailedTransfers());
                System.out.println("Average transfer rate: " + formatFileSize((long) stats.getAverageTransferRate()) + "/s");
                System.out.println("Uptime: " + formatUptime(stats.getUptimeMillis()));
            } else {
                System.out.println("Server stopped.");
            }

        } catch (Exception e) {
            System.err.println("Error: Failed to stop server: " + e.getMessage());
            return false;
        } finally {
            // Clean up resources if we created them
            if (networkService == null && service != null) {
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
     * Displays detailed help information for the server stop command.
     */
    private void displayHelp() {
        System.out.println("Server Stop Command Help");
        System.out.println("==========================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --force               Force stop even if transfers are in progress");
        System.out.println("  --quiet, -q           Quiet mode with minimal output");
        System.out.println("  --help                Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  server stop");
        System.out.println("  server stop --force");
        System.out.println("  server stop --quiet");
    }

    /**
     * Displays server status information.
     *
     * @param service the network service
     */
    private void displayServerStatus(NetworkService service) {
        try {
            NetworkService.NetworkStatistics stats = service.getStatistics();
            
            System.out.println("Server Status: " + (service.isServerRunning() ? "RUNNING" : "STOPPED"));
            System.out.println("Listening Port: " + service.getServerPort());
            System.out.println("Active Connections: " + service.getActiveConnectionCount());
            System.out.println("Active Transfers: " + service.getActiveTransferCount());
            System.out.println("Default Transport: " + service.getDefaultTransportType());
            System.out.println();
            System.out.println("Statistics:");
            System.out.println("  Bytes sent: " + formatFileSize(stats.getTotalBytesSent()));
            System.out.println("  Bytes received: " + formatFileSize(stats.getTotalBytesReceived()));
            System.out.println("  Messages sent: " + stats.getTotalMessagesSent());
            System.out.println("  Messages received: " + stats.getTotalMessagesReceived());
            System.out.println("  Completed transfers: " + stats.getCompletedTransfers());
            System.out.println("  Failed transfers: " + stats.getFailedTransfers());
            System.out.println("  Average transfer rate: " + formatFileSize((long) stats.getAverageTransferRate()) + "/s");
            System.out.println("  Uptime: " + formatUptime(stats.getUptimeMillis()));
            
        } catch (Exception e) {
            System.err.println("Error retrieving server status: " + e.getMessage());
        }
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

    /**
     * Formats uptime in human-readable format.
     *
     * @param uptimeMillis the uptime in milliseconds
     * @return formatted uptime string
     */
    private String formatUptime(long uptimeMillis) {
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        hours = hours % 24;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");
        
        return sb.toString();
    }
}