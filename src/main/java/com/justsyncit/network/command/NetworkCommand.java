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

package com.justsyncit.network.command;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.justsyncit.command.Command;
import com.justsyncit.command.CommandContext;
import com.justsyncit.network.NetworkService;
import com.justsyncit.network.transfer.FileTransferResult;
import com.justsyncit.storage.ContentStore;

/**
 * Command for managing network operations.
 * Follows Single Responsibility Principle by handling only network-related
 * commands.
 */
public class NetworkCommand implements Command {

    /** The logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(NetworkCommand.class);

    /** The network service for network operations. */
    private final NetworkService networkService;
    /** The content store for file operations. */
    private final ContentStore contentStore;

    /**
     * Creates a new network command.
     *
     * @param networkService the network service
     * @param contentStore   the content store
     */
    private NetworkCommand(NetworkService networkService, ContentStore contentStore) {
        // Store references to services - these are injected dependencies
        this.networkService = networkService;
        this.contentStore = contentStore;
    }

    /**
     * Creates a new network command with validation.
     *
     * @param networkService the network service
     * @param contentStore   the content store
     * @return a new NetworkCommand instance
     * @throws IllegalArgumentException if any parameter is null
     */
    public static NetworkCommand create(NetworkService networkService, ContentStore contentStore) {
        // Validate parameters before object creation
        if (networkService == null) {
            throw new IllegalArgumentException("NetworkService cannot be null");
        }
        if (contentStore == null) {
            throw new IllegalArgumentException("ContentStore cannot be null");
        }
        return new NetworkCommand(networkService, contentStore);
    }

    @Override
    public String getName() {
        return "network";
    }

    @Override
    public String getDescription() {
        return "Manage network connections and file transfers";
    }

    @Override
    public String getUsage() {
        return "network <start|stop|connect|disconnect|send|status|help> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (args.length == 0) {
            System.out.println("Usage: " + getUsage());
            return false;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        try {
            switch (subCommand) {
                case "start":
                    return handleStart(context, args);
                case "stop":
                    return handleStop(context, args);
                case "connect":
                    return handleConnect(context, args);
                case "disconnect":
                    return handleDisconnect(context, args);
                case "send":
                    return handleSend(context, args);
                case "status":
                    return handleStatus(context, args);
                case "help":
                    return handleHelp(context, args);
                default:
                    System.out.println("Unknown network command: " + subCommand);
                    System.out.println("Usage: " + getUsage());
                    return false;
            }
        } catch (Exception e) {
            System.err.println("Command execution failed: " + e.getMessage());
            logger.error("Command execution failed", e);
            return false;
        }
    }

    private boolean handleStart(CommandContext context, String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: network start <port>");
            return false;
        }

        try {
            int port = Integer.parseInt(args[1]);
            networkService.startServer(port)
                    .thenRun(() -> System.out.println("Network server started on port " + port))
                    .exceptionally(e -> {
                        Throwable cause = e;
                        if (cause instanceof java.util.concurrent.CompletionException) {
                            cause = cause.getCause();
                        }
                        logger.error("Failed to start network server", cause);
                        System.err.println("Failed to start network server: " + cause.getMessage());
                        return null;
                    })
                    .join();
            return true;
        } catch (NumberFormatException e) {
            logger.warn("Invalid port number provided: {}", args[1]);
            System.err.println("Invalid port number: " + args[1]);
            return false;
        } catch (Exception e) {
            logger.error("Failed to start network server", e);
            System.err.println("Failed to start network server: " + e.getMessage());
            return false;
        }
    }

    private boolean handleStop(CommandContext context, String[] args) {
        try {
            networkService.stopServer()
                    .thenRun(() -> System.out.println("Network server stopped"))
                    .exceptionally(e -> {
                        Throwable cause = e;
                        if (cause instanceof java.util.concurrent.CompletionException) {
                            cause = cause.getCause();
                        }
                        logger.error("Failed to stop network server", cause);
                        System.err.println("Failed to stop network server: " + cause.getMessage());
                        return null;
                    })
                    .join();
            return true;
        } catch (Exception e) {
            logger.error("Failed to stop network server", e);
            System.err.println("Failed to stop network server: " + e.getMessage());
            return false;
        }
    }

    private boolean handleConnect(CommandContext context, String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: network connect <host:port>");
            return false;
        }

        try {
            String[] hostPort = args[1].split(":");
            if (hostPort.length != 2) {
                System.out.println("Invalid address format. Use: host:port");
                return false;
            }

            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            InetSocketAddress address = new InetSocketAddress(host, port);

            networkService.connectToNode(address)
                    .thenRun(() -> System.out.println("Connected to " + address))
                    .exceptionally(e -> {
                        Throwable cause = e;
                        if (cause instanceof java.util.concurrent.CompletionException) {
                            cause = cause.getCause();
                        }
                        logger.error("Failed to connect to " + address, cause);
                        System.err.println("Failed to connect to " + address + ": " + cause.getMessage());
                        return null;
                    })
                    .join();
            return true;
        } catch (NumberFormatException e) {
            logger.warn("Invalid port number in address: {}", args[1]);
            System.err.println("Invalid port number in address: " + args[1]);
            return false;
        } catch (Exception e) {
            logger.error("Failed to connect", e);
            System.err.println("Failed to connect: " + e.getMessage());
            return false;
        }
    }

    private boolean handleDisconnect(CommandContext context, String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: network disconnect <host:port>");
            return false;
        }

        try {
            String[] hostPort = args[1].split(":");
            if (hostPort.length != 2) {
                System.out.println("Invalid address format. Use: host:port");
                return false;
            }

            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            InetSocketAddress address = new InetSocketAddress(host, port);

            networkService.disconnectFromNode(address)
                    .thenRun(() -> System.out.println("Disconnected from " + address))
                    .exceptionally(e -> {
                        Throwable cause = e;
                        if (cause instanceof java.util.concurrent.CompletionException) {
                            cause = cause.getCause();
                        }
                        logger.error("Failed to disconnect from " + address, cause);
                        System.err.println("Failed to disconnect from " + address + ": " + cause.getMessage());
                        return null;
                    })
                    .join();
            return true;
        } catch (NumberFormatException e) {
            logger.warn("Invalid port number in address: {}", args[1]);
            System.err.println("Invalid port number in address: " + args[1]);
            return false;
        } catch (Exception e) {
            logger.error("Failed to disconnect", e);
            System.err.println("Failed to disconnect: " + e.getMessage());
            return false;
        }
    }

    private boolean handleSend(CommandContext context, String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: network send <file> <host:port>");
            return false;
        }

        try {
            Path filePath = Path.of(args[1]);
            if (!Files.exists(filePath)) {
                System.out.println("File not found: " + filePath);
                return false;
            }

            String[] hostPort = args[2].split(":");
            if (hostPort.length != 2) {
                System.out.println("Invalid address format. Use: host:port");
                return false;
            }

            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            InetSocketAddress remoteAddress = new InetSocketAddress(host, port);

            Instant start = Instant.now();
            FileTransferResult result = networkService.sendFile(filePath, remoteAddress, contentStore)
                    .exceptionally(e -> {
                        Throwable cause = e;
                        if (cause instanceof java.util.concurrent.CompletionException) {
                            cause = cause.getCause();
                        }
                        logger.error("File transfer failed", cause);
                        System.err.println("File transfer failed: " + cause.getMessage());
                        return null;
                    })
                    .join();

            if (result != null && result.isSuccess()) {
                Duration duration = Duration.between(start, Instant.now());
                double rateKBps = (result.getBytesTransferred() / 1024.0) / (duration.toMillis() / 1000.0);
                System.out.printf("File transferred successfully: %d bytes in %.2f seconds (%.2f KB/s)%n",
                        result.getBytesTransferred(), duration.toMillis() / 1000.0, rateKBps);
                return true;
            } else {
                logger.warn("File transfer reported failure via result status");
                System.err.println("File transfer failed");
                return false;
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid port number in address: {}", args[2]);
            System.err.println("Invalid port number in address: " + args[2]);
            return false;
        } catch (Exception e) {
            logger.error("Failed to send file", e);
            System.err.println("Failed to send file: " + e.getMessage());
            return false;
        }
    }

    private boolean handleStatus(CommandContext context, String[] args) {
        try {
            System.out.println("Network Status:");
            System.out.println("  Server running: " + networkService.isServerRunning());
            System.out.println("  Active connections: " + networkService.getActiveConnectionCount());
            System.out.println("  Active transfers: " + networkService.getActiveTransferCount());
            System.out.println("  Bytes sent: " + formatBytes(networkService.getBytesSent()));
            System.out.println("  Bytes received: " + formatBytes(networkService.getBytesReceived()));
            System.out.println("  Messages sent: " + networkService.getMessagesSent());
            System.out.println("  Messages received: " + networkService.getMessagesReceived());
            return true;
        } catch (Exception e) {
            logger.error("Failed to get network status", e);
            System.err.println("Failed to get network status: " + e.getMessage());
            return false;
        }
    }

    private boolean handleHelp(CommandContext context, String[] args) {
        System.out.println("Network Commands:");
        System.out.println("  network start <port>           - Start network server on specified port");
        System.out.println("  network stop                    - Stop network server");
        System.out.println("  network connect <host:port>     - Connect to remote node");
        System.out.println("  network disconnect <host:port>  - Disconnect from remote node");
        System.out.println("  network send <file> <host:port>  - Send file to remote node");
        System.out.println("  network status                  - Show network statistics");
        System.out.println("  network help                    - Show this help message");
        return true;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}