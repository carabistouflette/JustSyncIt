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
 * Command for showing server status and configuration.
 * Follows Single Responsibility Principle by handling only server status operations.
 */
public class ServerStatusCommand implements Command {

    private final NetworkService networkService;
    private final ServiceFactory serviceFactory;

    /**
     * Creates a server status command with dependency injection.
     *
     * @param networkService network service (may be null for lazy initialization)
     */
    public ServerStatusCommand(NetworkService networkService) {
        this.networkService = networkService;
        this.serviceFactory = new ServiceFactory();
    }

    @Override
    public String getName() {
        return "server";
    }

    @Override
    public String getDescription() {
        return "Show server status and configuration";
    }

    @Override
    public String getUsage() {
        return "server status [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Handle help option first
        if (args.length == 1 && args[0].equals("--help")) {
            displayHelp();
            return true;
        }

        // Check for subcommand
        if (args.length == 0 || !args[0].equals("status")) {
            System.err.println("Error: Missing subcommand 'status'");
            System.err.println(getUsage());
            System.err.println("Use 'help server status' for more information");
            return false;
        }

        // Parse options
        boolean verbose = false;
        boolean json = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                case "--json":
                    json = true;
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
            if (json) {
                displayJsonStatus(service, verbose);
            } else {
                displayTextStatus(service, verbose);
            }

        } catch (Exception e) {
            System.err.println("Error: Failed to get server status: " + e.getMessage());
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
     * Displays server status in text format.
     *
     * @param service network service
     * @param verbose whether to show verbose output
     */
    private void displayTextStatus(NetworkService service, boolean verbose) {
        System.out.println("JustSyncIt Server Status");
        System.out.println("========================");
        System.out.println();

        // Basic status
        System.out.println("Server Status: " + (service.isServerRunning() ? "RUNNING" : "STOPPED"));
        System.out.println("Service Status: " + (service.isRunning() ? "ACTIVE" : "INACTIVE"));

        if (service.isServerRunning()) {
            System.out.println("Listening Port: " + service.getServerPort());
            System.out.println("Default Transport: " + service.getDefaultTransportType());
            System.out.println("Active Connections: " + service.getActiveConnectionCount());
            System.out.println("Active Transfers: " + service.getActiveTransferCount());
        } else {
            System.out.println("Server is not running");
        }

        System.out.println();

        if (verbose || service.isServerRunning()) {
            // Statistics
            NetworkService.NetworkStatistics stats = service.getStatistics();
            
            System.out.println("Statistics:");
            System.out.println("-----------");
            System.out.println("Total Bytes Sent: " + formatFileSize(stats.getTotalBytesSent()));
            System.out.println("Total Bytes Received: " + formatFileSize(stats.getTotalBytesReceived()));
            System.out.println("Total Messages Sent: " + stats.getTotalMessagesSent());
            System.out.println("Total Messages Received: " + stats.getTotalMessagesReceived());
            System.out.println("Completed Transfers: " + stats.getCompletedTransfers());
            System.out.println("Failed Transfers: " + stats.getFailedTransfers());
            System.out.println("Average Transfer Rate: " + formatFileSize((long) stats.getAverageTransferRate()) + "/s");
            System.out.println("Uptime: " + formatUptime(stats.getUptimeMillis()));
            
            if (verbose) {
                System.out.println();
                System.out.println("Detailed Information:");
                System.out.println("--------------------");
                
                // Calculate additional statistics
                long totalTransfers = stats.getCompletedTransfers() + stats.getFailedTransfers();
                double successRate = totalTransfers > 0 ? 
                    (stats.getCompletedTransfers() * 100.0 / totalTransfers) : 0.0;
                
                System.out.println("Success Rate: " + String.format("%.2f%%", successRate));
                System.out.println("Total Transfers Attempted: " + totalTransfers);
                
                if (service.isServerRunning()) {
                    System.out.println("Current Transfer Rate: " + 
                        formatFileSize((long) stats.getAverageTransferRate()) + "/s");
                }
                
                // Connection information
                System.out.println("Connection Capacity: " + service.getActiveConnectionCount() + " active");
                
                // Performance metrics
                long uptimeSeconds = stats.getUptimeMillis() / 1000;
                if (uptimeSeconds > 0) {
                    double throughputPerSecond = stats.getTotalBytesReceived() / (double) uptimeSeconds;
                    System.out.println("Average Throughput: " + formatFileSize((long) throughputPerSecond) + "/s");
                }
            }
        }
    }

    /**
     * Displays server status in JSON format.
     *
     * @param service network service
     * @param verbose whether to include verbose information
     */
    private void displayJsonStatus(NetworkService service, boolean verbose) {
        NetworkService.NetworkStatistics stats = service.getStatistics();
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"server\": {\n");
        json.append("    \"running\": ").append(service.isServerRunning()).append(",\n");
        json.append("    \"serviceActive\": ").append(service.isRunning()).append(",\n");
        
        if (service.isServerRunning()) {
            json.append("    \"port\": ").append(service.getServerPort()).append(",\n");
            json.append("    \"defaultTransport\": \"").append(service.getDefaultTransportType()).append("\",\n");
            json.append("    \"activeConnections\": ").append(service.getActiveConnectionCount()).append(",\n");
            json.append("    \"activeTransfers\": ").append(service.getActiveTransferCount()).append(",\n");
        }
        
        json.append("    \"uptimeMillis\": ").append(stats.getUptimeMillis()).append("\n");
        json.append("  },\n");
        
        json.append("  \"statistics\": {\n");
        json.append("    \"totalBytesSent\": ").append(stats.getTotalBytesSent()).append(",\n");
        json.append("    \"totalBytesReceived\": ").append(stats.getTotalBytesReceived()).append(",\n");
        json.append("    \"totalMessagesSent\": ").append(stats.getTotalMessagesSent()).append(",\n");
        json.append("    \"totalMessagesReceived\": ").append(stats.getTotalMessagesReceived()).append(",\n");
        json.append("    \"completedTransfers\": ").append(stats.getCompletedTransfers()).append(",\n");
        json.append("    \"failedTransfers\": ").append(stats.getFailedTransfers()).append(",\n");
        json.append("    \"averageTransferRate\": ").append(stats.getAverageTransferRate()).append("\n");
        json.append("  }");
        
        if (verbose) {
            long totalTransfers = stats.getCompletedTransfers() + stats.getFailedTransfers();
            double successRate = totalTransfers > 0 ? 
                (stats.getCompletedTransfers() * 100.0 / totalTransfers) : 0.0;
            
            long uptimeSeconds = stats.getUptimeMillis() / 1000;
            double throughputPerSecond = uptimeSeconds > 0 ? 
                stats.getTotalBytesReceived() / (double) uptimeSeconds : 0.0;
            
            json.append(",\n");
            json.append("  \"detailed\": {\n");
            json.append("    \"successRate\": ").append(String.format("%.2f", successRate)).append(",\n");
            json.append("    \"totalTransfersAttempted\": ").append(totalTransfers).append(",\n");
            json.append("    \"averageThroughputPerSecond\": ").append((long) throughputPerSecond).append("\n");
            json.append("  }");
        }
        
        json.append("\n}");
        
        System.out.println(json.toString());
    }

    /**
     * Displays detailed help information for the server status command.
     */
    private void displayHelp() {
        System.out.println("Server Status Command Help");
        System.out.println("============================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --verbose, -v          Show detailed status information");
        System.out.println("  --json                 Output status in JSON format");
        System.out.println("  --help                 Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  server status");
        System.out.println("  server status --verbose");
        System.out.println("  server status --json");
        System.out.println("  server status --verbose --json");
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
     * @param uptimeMillis uptime in milliseconds
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