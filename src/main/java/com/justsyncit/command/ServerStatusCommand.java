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

/**
 * Command for showing server status and configuration.
 * Follows Single Responsibility Principle by handling only server status
 * operations.
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

        StatusOptions options;
        try {
            options = parseOptions(args);
        } catch (IllegalArgumentException e) {
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
                    System.err.println("Error: Failed to initialize network service: " + e.getMessage());
                    return false;
                }
            }

            if (options.json) {
                displayJsonStatus(service, options.verbose);
            } else {
                displayTextStatus(service, options.verbose);
            }

            return true;

        } catch (Exception e) {
            System.err.println("Error: Failed to get server status: " + e.getMessage());
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
    private StatusOptions parseOptions(String[] args) {
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
                    return null;
                default:
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Error: Unknown option: " + arg);
                    }
                    break;
            }
        }

        return new StatusOptions(verbose, json);
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

        displayBasicStatus(service);
        System.out.println();

        if (verbose || service.isServerRunning()) {
            displayStatistics(service, verbose);
        }
    }

    /**
     * Displays basic server status information.
     *
     * @param service network service
     */
    private void displayBasicStatus(NetworkService service) {
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
    }

    /**
     * Displays statistics information.
     *
     * @param service network service
     * @param verbose whether to show verbose output
     */
    private void displayStatistics(NetworkService service, boolean verbose) {
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
            displayDetailedStatistics(service, stats);
        }
    }

    /**
     * Displays detailed statistics information.
     *
     * @param service network service
     * @param stats   network statistics
     */
    private void displayDetailedStatistics(NetworkService service, NetworkService.NetworkStatistics stats) {
        System.out.println();
        System.out.println("Detailed Information:");
        System.out.println("--------------------");

        double successRate = calculateSuccessRate(stats);
        long totalTransfers = stats.getCompletedTransfers() + stats.getFailedTransfers();

        System.out.println("Success Rate: " + String.format("%.2f%%", successRate));
        System.out.println("Total Transfers Attempted: " + totalTransfers);

        if (service.isServerRunning()) {
            System.out.println("Current Transfer Rate: "
                    + formatFileSize((long) stats.getAverageTransferRate()) + "/s");
        }

        System.out.println("Connection Capacity: " + service.getActiveConnectionCount() + " active");

        double throughputPerSecond = calculateThroughputPerSecond(stats);
        if (throughputPerSecond > 0) {
            System.out.println("Average Throughput: " + formatFileSize((long) throughputPerSecond) + "/s");
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

        StringBuilder json = new StringBuilder(512);
        json.append("{\n");
        appendServerInfo(json, service, stats);
        json.append(",\n");
        appendStatistics(json, stats);

        if (verbose) {
            json.append(",\n");
            appendDetailedInfo(json, stats);
        }

        json.append("\n}");
        System.out.println(json.toString());
    }

    /**
     * Appends server information to JSON output.
     *
     * @param json    StringBuilder to append to
     * @param service network service
     * @param stats   network statistics
     */
    private void appendServerInfo(StringBuilder json, NetworkService service, NetworkService.NetworkStatistics stats) {
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
        json.append("  }");
    }

    /**
     * Appends statistics to JSON output.
     *
     * @param json  StringBuilder to append to
     * @param stats network statistics
     */
    private void appendStatistics(StringBuilder json, NetworkService.NetworkStatistics stats) {
        json.append("  \"statistics\": {\n");
        json.append("    \"totalBytesSent\": ").append(stats.getTotalBytesSent()).append(",\n");
        json.append("    \"totalBytesReceived\": ").append(stats.getTotalBytesReceived()).append(",\n");
        json.append("    \"totalMessagesSent\": ").append(stats.getTotalMessagesSent()).append(",\n");
        json.append("    \"totalMessagesReceived\": ").append(stats.getTotalMessagesReceived()).append(",\n");
        json.append("    \"completedTransfers\": ").append(stats.getCompletedTransfers()).append(",\n");
        json.append("    \"failedTransfers\": ").append(stats.getFailedTransfers()).append(",\n");
        json.append("    \"averageTransferRate\": ").append(stats.getAverageTransferRate()).append("\n");
        json.append("  }");
    }

    /**
     * Appends detailed information to JSON output.
     *
     * @param json  StringBuilder to append to
     * @param stats network statistics
     */
    private void appendDetailedInfo(StringBuilder json, NetworkService.NetworkStatistics stats) {
        double successRate = calculateSuccessRate(stats);
        double throughputPerSecond = calculateThroughputPerSecond(stats);
        long totalTransfers = stats.getCompletedTransfers() + stats.getFailedTransfers();

        json.append("  \"detailed\": {\n");
        json.append("    \"successRate\": ").append(String.format("%.2f", successRate)).append(",\n");
        json.append("    \"totalTransfersAttempted\": ").append(totalTransfers).append(",\n");
        json.append("    \"averageThroughputPerSecond\": ").append((long) throughputPerSecond).append("\n");
        json.append("  }");
    }

    /**
     * Calculates success rate from statistics.
     *
     * @param stats network statistics
     * @return success rate as percentage
     */
    private double calculateSuccessRate(NetworkService.NetworkStatistics stats) {
        long totalTransfers = stats.getCompletedTransfers() + stats.getFailedTransfers();
        return totalTransfers > 0 ? (stats.getCompletedTransfers() * 100.0 / totalTransfers) : 0.0;
    }

    /**
     * Calculates average throughput per second.
     *
     * @param stats network statistics
     * @return throughput in bytes per second
     */
    private double calculateThroughputPerSecond(NetworkService.NetworkStatistics stats) {
        long uptimeSeconds = stats.getUptimeMillis() / 1000;
        return uptimeSeconds > 0 ? stats.getTotalBytesReceived() / (double) uptimeSeconds : 0.0;
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
                System.err.println("Warning: Failed to close resource: " + e.getMessage());
            }
        }
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
     * Formats uptime in human-readable format.
     *
     * @param uptimeMillis uptime in milliseconds
     * @return formatted uptime string
     */
    private String formatUptime(long uptimeMillis) {
        final long MILLIS_PER_SECOND = 1000;
        final long SECONDS_PER_MINUTE = 60;
        final long MINUTES_PER_HOUR = 60;
        final long HOURS_PER_DAY = 24;

        long seconds = uptimeMillis / MILLIS_PER_SECOND;
        long minutes = seconds / SECONDS_PER_MINUTE;
        long hours = minutes / MINUTES_PER_HOUR;
        long days = hours / HOURS_PER_DAY;

        seconds = seconds % SECONDS_PER_MINUTE;
        minutes = minutes % MINUTES_PER_HOUR;
        hours = hours % HOURS_PER_DAY;

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

    /**
     * Simple data class to hold status options.
     */
    private static class StatusOptions {
        final boolean verbose;
        final boolean json;

        StatusOptions(boolean verbose, boolean json) {
            this.verbose = verbose;
            this.json = json;
        }
    }
}