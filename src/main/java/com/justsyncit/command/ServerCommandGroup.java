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

/**
 * Command group for server operations.
 * This command delegates to specific server subcommands.
 * Follows Composite Pattern by grouping related commands together.
 */
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings({"EI_EXPOSE_REP2", "EI_EXPOSE_REP"})
public class ServerCommandGroup implements Command {

    private static final String SUBCOMMAND_START = "start";
    private static final String SUBCOMMAND_STOP = "stop";
    private static final String SUBCOMMAND_STATUS = "status";

    private final ServerStartCommand startCommand;
    private final ServerStopCommand stopCommand;
    private final ServerStatusCommand statusCommand;

    /**
     * Creates a server command group with default subcommands.
     */
    public ServerCommandGroup() {
        this(new ServerStartCommand(null),
                new ServerStopCommand(null),
                new ServerStatusCommand(null));
    }

    /**
     * Creates a server command group with injected subcommands.
     *
     * @param startCommand  the start command
     * @param stopCommand   the stop command
     * @param statusCommand the status command
     */
    public ServerCommandGroup(ServerStartCommand startCommand,
            ServerStopCommand stopCommand,
            ServerStatusCommand statusCommand) {
        this.startCommand = startCommand;
        this.stopCommand = stopCommand;
        this.statusCommand = statusCommand;
    }

    @Override
    public String getName() {
        return "server";
    }

    @Override
    public String getDescription() {
        return "Manage backup server (start, stop, status)";
    }

    @Override
    public String getUsage() {
        return "server <subcommand> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        // Check for help first
        if (isHelpRequested(args)) {
            displayHelp();
            return true;
        }

        if (args.length == 0) {
            displayMissingSubcommandError();
            return false;
        }

        String subcommand = args[0];
        // We pass the full args array to subcommands because they validation
        // expects the subcommand name to be the first argument (e.g. "start", "stop")

        switch (subcommand) {
            case SUBCOMMAND_START:
                return startCommand.execute(args, context);
            case SUBCOMMAND_STOP:
                return stopCommand.execute(args, context);
            case SUBCOMMAND_STATUS:
                return statusCommand.execute(args, context);
            case "help":
                displayHelp();
                return true;
            default:
                displayUnknownSubcommandError(subcommand);
                return false;
        }
    }

    /**
     * Displays error message for missing subcommand.
     */
    private void displayMissingSubcommandError() {
        System.err.println("Error: Missing subcommand");
        displayAvailableSubcommands();
    }

    /**
     * Displays error message for unknown subcommand.
     *
     * @param subcommand the unknown subcommand
     */
    private void displayUnknownSubcommandError(String subcommand) {
        System.err.println("Error: Unknown subcommand: " + subcommand);
        displayAvailableSubcommands();
    }

    /**
     * Displays the list of available subcommands.
     */
    private void displayAvailableSubcommands() {
        System.err.println("Available subcommands: " + SUBCOMMAND_START + ", "
                + SUBCOMMAND_STOP + ", " + SUBCOMMAND_STATUS);
        System.err.println("Use 'help server' for more information");
    }

    /**
     * Displays detailed help information for the server command group.
     */
    private void displayHelp() {
        System.out.println("Server Command Group Help");
        System.out.println("===========================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Subcommands:");
        System.out.println("  " + SUBCOMMAND_START + "        Start a backup server");
        System.out.println("  " + SUBCOMMAND_STOP + "         Stop a running backup server");
        System.out.println("  " + SUBCOMMAND_STATUS + "       Show server status and configuration");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  server " + SUBCOMMAND_START);
        System.out.println("  server " + SUBCOMMAND_STOP);
        System.out.println("  server " + SUBCOMMAND_STATUS);
        System.out.println();
        System.out.println("For detailed help on a specific subcommand, use:");
        System.out.println("  help server " + SUBCOMMAND_START);
        System.out.println("  help server " + SUBCOMMAND_STOP);
        System.out.println("  help server " + SUBCOMMAND_STATUS);
    }
}