package com.justsyncit.command;

import com.justsyncit.ServiceFactory;
import com.justsyncit.network.NetworkService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for stopping a backup server.
 * Follows Single Responsibility Principle by handling only server stop
 * operations.
 */

public class ServerStopCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(ServerStopCommand.class);

    private static final long KB = 1024;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;
    private static final long MILLIS_PER_SECOND = 1000;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long MINUTES_PER_HOUR = 60;
    private static final long HOURS_PER_DAY = 24;

    private final NetworkService networkService;
    private final ServiceFactory serviceFactory;
    private final com.justsyncit.ApplicationInfoDisplay console;

    /**
     * Creates a server stop command with dependency injection.
     *
     * @param networkService network service (may be null for lazy initialization)
     * @param console        the console display
     */
    public ServerStopCommand(NetworkService networkService, com.justsyncit.ApplicationInfoDisplay console) {
        this.networkService = networkService;
        this.serviceFactory = new ServiceFactory();
        this.console = console;
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
        if (isHelpRequested(args)) {
            displayHelp();
            return true;
        }

        // Check for subcommand
        if (args.length == 0 || !args[0].equals("stop")) {
            logger.error("Missing subcommand 'stop'");
            console.displayError("Error: Missing subcommand 'stop'");
            console.displayError(getUsage());
            console.displayError("Use 'help server stop' for more information");
            return false;
        }

        StopOptions options;
        try {
            options = parseOptions(args);
        } catch (IllegalArgumentException e) {
            console.displayError(e.getMessage());
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
                    service = localService;
                } catch (Exception e) {
                    logger.error("Failed to initialize network service", e);
                    console.displayError("Error: Failed to initialize network service: " + e.getMessage());
                    return false;
                }
            }

            return stopServer(service, options);

        } catch (Exception e) {
            logger.error("Failed to stop server", e);
            console.displayError("Error: Failed to stop server: " + e.getMessage());
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
    private StopOptions parseOptions(String[] args) {
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
                    return null;
                default:
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Error: Unknown option: " + arg);
                    }
                    break;
            }
        }

        return new StopOptions(force, verbose);
    }

    /**
     * Stops the server with the given options.
     *
     * @param service network service
     * @param options stop options
     * @return true if successful
     * @throws Exception if server fails to stop
     */
    private boolean stopServer(NetworkService service, StopOptions options) throws Exception {
        // Check if server is running
        // Check if server is running
        if (!service.isServerRunning()) {
            logger.warn("Server is not running");
            console.displayError("Error: Server is not running");
            return false;
        }

        // Check for active transfers and warn if not forcing
        int activeTransfers = service.getActiveTransferCount();
        if (activeTransfers > 0 && !options.force) {
            logger.warn("Server has {} active transfer(s) in progress, stopping aborted", activeTransfers);
            console.displayError("Error: Server has " + activeTransfers + " active transfer(s) in progress");
            console.displayError("Use --force to stop the server anyway");
            return false;
        }

        // Show server status before stopping
        if (options.verbose) {
            displayPreStopStatus(service);
            if (activeTransfers > 0 && options.force) {
                console.displayInfo("Warning: Forcing server stop with " + activeTransfers + " active transfer(s)");
            }
        }

        // Stop the server
        if (options.verbose) {
            console.displayInfo("Stopping server...");
        }

        service.stopServer().get();

        // Display results
        if (options.verbose) {
            displayStopSuccess(service);
        } else {
            console.displayInfo("Server stopped.");
        }

        return true;
    }

    /**
     * Displays server status before stopping.
     *
     * @param service network service
     */
    private void displayPreStopStatus(NetworkService service) {
        console.displayInfo("Server Status Before Stop:");
        console.displayInfo("=========================");
        displayServerStatus(service);
        console.displayInfo("");
    }

    /**
     * Displays success message and final statistics.
     *
     * @param service network service
     */
    private void displayStopSuccess(NetworkService service) {
        console.displayInfo("Server stopped successfully!");

        // Show final statistics
        NetworkService.NetworkStatistics stats = service.getStatistics();
        console.displayInfo("");
        console.displayInfo("Final Statistics:");
        console.displayInfo("=================");
        console.displayInfo("Total bytes sent: " + formatFileSize(stats.getTotalBytesSent()));
        console.displayInfo("Total bytes received: " + formatFileSize(stats.getTotalBytesReceived()));
        console.displayInfo("Total messages sent: " + stats.getTotalMessagesSent());
        console.displayInfo("Total messages received: " + stats.getTotalMessagesReceived());
        console.displayInfo("Completed transfers: " + stats.getCompletedTransfers());
        console.displayInfo("Failed transfers: " + stats.getFailedTransfers());
        console.displayInfo("Average transfer rate: " + formatFileSize((long) stats.getAverageTransferRate()) + "/s");
        console.displayInfo("Uptime: " + formatUptime(stats.getUptimeMillis()));
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
            System.out.println(
                    "  Average transfer rate: " + formatFileSize((long) stats.getAverageTransferRate()) + "/s");
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
     * @param uptimeMillis the uptime in milliseconds
     * @return formatted uptime string
     */
    private String formatUptime(long uptimeMillis) {
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
     * Simple data class to hold stop options.
     */
    private static class StopOptions {
        final boolean force;
        final boolean verbose;

        StopOptions(boolean force, boolean verbose) {
            this.force = force;
            this.verbose = verbose;
        }
    }
}