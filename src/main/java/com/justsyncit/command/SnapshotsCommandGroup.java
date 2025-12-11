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
 * Follows Composite Pattern by grouping related commands together.
 */
public class SnapshotsCommandGroup implements Command {

    private static final String SUBCOMMAND_LIST = "list";
    private static final String SUBCOMMAND_INFO = "info";
    private static final String SUBCOMMAND_DELETE = "delete";
    private static final String SUBCOMMAND_VERIFY = "verify";

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
        String[] subcommandArgs = java.util.Arrays.copyOfRange(args, 1, args.length);

        switch (subcommand) {
            case SUBCOMMAND_LIST:
                return listCommand.execute(subcommandArgs, context);
            case SUBCOMMAND_INFO:
                return infoCommand.execute(subcommandArgs, context);
            case SUBCOMMAND_DELETE:
                return deleteCommand.execute(subcommandArgs, context);
            case SUBCOMMAND_VERIFY:
                return verifyCommand.execute(subcommandArgs, context);
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
        System.err.println("Available subcommands: " + SUBCOMMAND_LIST + ", "
                + SUBCOMMAND_INFO + ", " + SUBCOMMAND_DELETE + ", " + SUBCOMMAND_VERIFY);
        System.err.println("Use 'help snapshots' for more information");
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
        System.out.println("  " + SUBCOMMAND_LIST + "        List all available snapshots");
        System.out.println("  " + SUBCOMMAND_INFO + "        Show detailed information about a specific snapshot");
        System.out.println("  " + SUBCOMMAND_DELETE + "      Delete a specific snapshot");
        System.out.println("  " + SUBCOMMAND_VERIFY + "      Verify integrity of a snapshot");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  snapshots " + SUBCOMMAND_LIST);
        System.out.println("  snapshots " + SUBCOMMAND_INFO + " abc123-def456");
        System.out.println("  snapshots " + SUBCOMMAND_DELETE + " abc123-def456");
        System.out.println("  snapshots " + SUBCOMMAND_VERIFY + " abc123-def456");
        System.out.println();
        System.out.println("For detailed help on a specific subcommand, use:");
        System.out.println("  help snapshots " + SUBCOMMAND_LIST);
        System.out.println("  help snapshots " + SUBCOMMAND_INFO);
        System.out.println("  help snapshots " + SUBCOMMAND_DELETE);
        System.out.println("  help snapshots " + SUBCOMMAND_VERIFY);
    }
}