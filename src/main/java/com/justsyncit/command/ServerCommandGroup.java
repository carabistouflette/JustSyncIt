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
 */
public class ServerCommandGroup implements Command {

    private final ServerStartCommand startCommand;
    private final ServerStopCommand stopCommand;
    private final ServerStatusCommand statusCommand;

    /**
     * Creates a server command group.
     */
    public ServerCommandGroup() {
        this.startCommand = new ServerStartCommand(null);
        this.stopCommand = new ServerStopCommand(null);
        this.statusCommand = new ServerStatusCommand(null);
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
        if (args.length == 0) {
            System.err.println("Error: Missing subcommand");
            System.err.println("Available subcommands: start, stop, status");
            System.err.println("Use 'help server' for more information");
            return false;
        }

        String subcommand = args[0];
        String[] subcommandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subcommandArgs, 0, args.length - 1);

        switch (subcommand) {
            case "start":
                return startCommand.execute(subcommandArgs, context);
            case "stop":
                return stopCommand.execute(subcommandArgs, context);
            case "status":
                return statusCommand.execute(subcommandArgs, context);
            case "--help":
            case "help":
                displayHelp();
                return true;
            default:
                System.err.println("Error: Unknown subcommand: " + subcommand);
                System.err.println("Available subcommands: start, stop, status");
                System.err.println("Use 'help server' for more information");
                return false;
        }
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
        System.out.println("  start        Start a backup server");
        System.out.println("  stop         Stop a running backup server");
        System.out.println("  status       Show server status and configuration");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  server start");
        System.out.println("  server stop");
        System.out.println("  server status");
        System.out.println();
        System.out.println("For detailed help on a specific subcommand, use:");
        System.out.println("  help server start");
        System.out.println("  help server stop");
        System.out.println("  help server status");
    }
}