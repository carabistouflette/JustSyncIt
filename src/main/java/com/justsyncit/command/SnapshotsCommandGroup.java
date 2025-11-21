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
 * Command group for snapshot management operations.
 * This command delegates to specific snapshot subcommands.
 */
public class SnapshotsCommandGroup implements Command {

    private final SnapshotsListCommand listCommand;
    private final SnapshotsInfoCommand infoCommand;
    private final SnapshotsDeleteCommand deleteCommand;
    private final SnapshotsVerifyCommand verifyCommand;

    /**
     * Creates a snapshots command group.
     */
    public SnapshotsCommandGroup() {
        this.listCommand = new SnapshotsListCommand(null);
        this.infoCommand = new SnapshotsInfoCommand(null);
        this.deleteCommand = new SnapshotsDeleteCommand(null);
        this.verifyCommand = new SnapshotsVerifyCommand(null);
    }

    @Override
    public String getName() {
        return "snapshots";
    }

    @Override
    public String getDescription() {
        return "Manage backup snapshots (list, info, delete, verify)";
    }

    @Override
    public String getUsage() {
        return "snapshots <subcommand> [options]";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (args.length == 0) {
            System.err.println("Error: Missing subcommand");
            System.err.println("Available subcommands: list, info, delete, verify");
            System.err.println("Use 'help snapshots' for more information");
            return false;
        }

        String subcommand = args[0];
        String[] subcommandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subcommandArgs, 0, args.length - 1);

        switch (subcommand) {
            case "list":
                return listCommand.execute(subcommandArgs, context);
            case "info":
                return infoCommand.execute(subcommandArgs, context);
            case "delete":
                return deleteCommand.execute(subcommandArgs, context);
            case "verify":
                return verifyCommand.execute(subcommandArgs, context);
            case "--help":
            case "help":
                displayHelp();
                return true;
            default:
                System.err.println("Error: Unknown subcommand: " + subcommand);
                System.err.println("Available subcommands: list, info, delete, verify");
                System.err.println("Use 'help snapshots' for more information");
                return false;
        }
    }

    /**
     * Displays detailed help information for the snapshots command group.
     */
    private void displayHelp() {
        System.out.println("Snapshots Command Group Help");
        System.out.println("=============================");
        System.out.println();
        System.out.println("Usage: " + getUsage());
        System.out.println();
        System.out.println("Description:");
        System.out.println("  " + getDescription());
        System.out.println();
        System.out.println("Subcommands:");
        System.out.println("  list        List all available snapshots");
        System.out.println("  info        Show detailed information about a specific snapshot");
        System.out.println("  delete      Delete a specific snapshot");
        System.out.println("  verify      Verify integrity of a snapshot");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  snapshots list");
        System.out.println("  snapshots info abc123-def456");
        System.out.println("  snapshots delete abc123-def456");
        System.out.println("  snapshots verify abc123-def456");
        System.out.println();
        System.out.println("For detailed help on a specific subcommand, use:");
        System.out.println("  help snapshots list");
        System.out.println("  help snapshots info");
        System.out.println("  help snapshots delete");
        System.out.println("  help snapshots verify");
    }
}