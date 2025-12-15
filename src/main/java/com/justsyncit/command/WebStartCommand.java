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

import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.web.WebServer;
import com.justsyncit.web.WebServerContext;

import java.util.logging.Logger;

/**
 * Command to start the web server for the management interface.
 * Usage: justsyncit web start [--port 8080]
 */
public final class WebStartCommand implements Command {

    private static final Logger LOGGER = Logger.getLogger(WebStartCommand.class.getName());
    private static final int DEFAULT_PORT = 8080;

    private static WebServer runningServer;

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
            // Create ServiceFactory to instantiate missing services
            com.justsyncit.ServiceFactory serviceFactory = new com.justsyncit.ServiceFactory();

            // Create BackupService and RestoreService
            ContentStore contentStore = context.getContentStore();
            MetadataService metadataService = context.getMetadataService();

            // Lazy initialization if not in context
            if (contentStore == null) {
                contentStore = serviceFactory.createContentStore(context.getBlake3Service());
            }
            if (metadataService == null) {
                metadataService = serviceFactory.createMetadataService();
            }

            com.justsyncit.backup.BackupService backupService = serviceFactory.createBackupService(
                    contentStore,
                    metadataService,
                    context.getBlake3Service());

            com.justsyncit.restore.RestoreService restoreService = serviceFactory.createRestoreService(
                    contentStore,
                    metadataService,
                    context.getBlake3Service());

            com.justsyncit.scheduler.SchedulerService schedulerService = serviceFactory
                    .createSchedulerService(backupService);
            schedulerService.start();

            // Create WebServerContext using Builder pattern
            WebServerContext webContext = WebServerContext.builder()
                    .withMetadataService(metadataService)
                    .withContentStore(contentStore)
                    .withBlake3Service(context.getBlake3Service())
                    .withBackupService(backupService)
                    .withRestoreService(restoreService)
                    .withSchedulerService(schedulerService)
                    .build();
            runningServer = new WebServer(port, webContext);
            runningServer.start();

            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║         JustSyncIt Web Interface Started           ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.printf("║  URL: http://localhost:%-28d║%n", port);
            System.out.println("║  Press Ctrl+C to stop the server                   ║");
            System.out.println("╚════════════════════════════════════════════════════╝");

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down web server...");
                if (runningServer != null) {
                    runningServer.stop();
                }
                if (schedulerService != null) {
                    schedulerService.stop();
                }
            }));

            // Block until interrupted
            Thread.currentThread().join();

            return true;
        } catch (Exception e) {
            LOGGER.severe("Failed to start web server: " + e.getMessage());
            System.err.println("Error: Failed to start web server: " + e.getMessage());
            return false;
        }
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
