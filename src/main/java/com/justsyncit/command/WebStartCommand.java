package com.justsyncit.command;

import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.web.WebServer;
import com.justsyncit.web.WebServerContext;
import com.justsyncit.scheduler.SchedulerService;
import com.justsyncit.backup.BackupService;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.hash.Blake3Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Command to start the web server for the management interface.
 * Usage: justsyncit web start [--port 8080]
 * 
 * Note: This server runs without authentication for single-user deployments.
 */
public final class WebStartCommand implements Command {

    private static final Logger logger = LoggerFactory.getLogger(WebStartCommand.class);
    private static final int DEFAULT_PORT = 8080;

    private static WebServer runningServer;

    /**
     * Latch to signal shutdown, avoiding Thread.currentThread().join() which blocks
     * indefinitely.
     */
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    @Override
    public String getName() {
        return "start";
    }

    @Override
    public String getDescription() {
        return "Start the web-based management interface";
    }

    @Override
    public String getUsage() {
        return "web start [--port <port>]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (isHelpRequested(args)) {
            System.out.println(getUsage());
            System.out.println("\nOptions:");
            System.out.println("  --port <port>  Port to listen on (default: 8080)");
            return true;
        }

        int port = DEFAULT_PORT;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[++i]);
                    if (port < 1 || port > 65535) {
                        System.err.println("Error: Port must be between 1 and 65535");
                        return false;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error: Invalid port number: " + args[i]);
                    return false;
                }
            }
        }

        // Check if server is already running
        if (runningServer != null && runningServer.isRunning()) {
            System.out.println("Web server is already running on port " + runningServer.getPort());
            return true;
        }

        try {
            // Retrieve services from context
            ContentStore contentStore = context.getContentStore();
            MetadataService metadataService = context.getMetadataService();
            BackupService backupService = context.getBackupService();
            RestoreService restoreService = context.getRestoreService();
            Blake3Service blake3Service = context.getBlake3Service();

            // Validate critical services
            if (contentStore == null || metadataService == null || backupService == null || restoreService == null) {
                System.err.println("Error: Critical services not available in command context.");
                return false;
            }

            // Retrieve scheduler service from context
            SchedulerService schedulerService = context.getSchedulerService();
            com.justsyncit.network.NetworkService networkService = context.getNetworkService();

            if (schedulerService == null) {
                System.err.println("Error: Scheduler service not available in command context.");
                return false;
            }

            schedulerService.start();

            // Create WebServerContext using Builder pattern
            WebServerContext webContext = WebServerContext.builder()
                    .withMetadataService(metadataService)
                    .withContentStore(contentStore)
                    .withBlake3Service(blake3Service)
                    .withBackupService(backupService)
                    .withRestoreService(restoreService)
                    .withSchedulerService(schedulerService)
                    .withNetworkService(networkService)
                    .withMasterPasswordService(context.getMasterPasswordService())
                    .build();
            runningServer = new WebServer(port, webContext);
            runningServer.start();

            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║         JustSyncIt Web Interface Started           ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.printf("║  URL: http://localhost:%-28d║%n", port);
            System.out.println("║  Press Ctrl+C to stop the server                   ║");
            System.out.println("╚════════════════════════════════════════════════════╝");

            // Add shutdown hook that signals the latch
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down web server...");
                if (runningServer != null) {
                    runningServer.stop();
                }
                if (schedulerService != null) {
                    schedulerService.stop();
                }
                shutdownLatch.countDown(); // Signal main thread to exit
            }));

            // Block until shutdown signal (Ctrl+C or SIGTERM)
            shutdownLatch.await();

            return true;
        } catch (Exception e) {
            handleError("Failed to start web server", e, logger);
            return false;
        }
    }

    private void handleError(String message, Exception e, Logger logger) {
        System.err.println("Error: " + message + ": " + e.getMessage());
        logger.error(message, e);
    }

    /**
     * Gets the running web server instance.
     *
     * @return the running server or null if not running
     */
    public static WebServer getRunningServer() {
        return runningServer;
    }
}
