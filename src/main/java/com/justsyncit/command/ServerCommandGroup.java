package com.justsyncit.command;

import com.justsyncit.ApplicationInfoDisplay;

/**
 * Command group for server operations.
 * This command delegates to specific server subcommands.
 * Follows Composite Pattern by grouping related commands together.
 */

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
    /**
     * Creates a server command group with default subcommands.
     */
    public ServerCommandGroup() {
        this(new com.justsyncit.ConsoleInfoDisplay());
    }

    private ServerCommandGroup(ApplicationInfoDisplay console) {
        this(new ServerStartCommand(null, console),
                new ServerStopCommand(null, console),
                new ServerStatusCommand(null, console),
                console);
    }

    private final ApplicationInfoDisplay console;

    /**
     * Creates a new ServerCommandGroup.
     *
     * @param startCommand  the start command
     * @param stopCommand   the stop command
     * @param statusCommand the status command
     * @param console       the console display
     */
    public ServerCommandGroup(ServerStartCommand startCommand,
            ServerStopCommand stopCommand,
            ServerStatusCommand statusCommand,
            ApplicationInfoDisplay console) {
        this.startCommand = startCommand;
        this.stopCommand = stopCommand;
        this.statusCommand = statusCommand;
        this.console = console != null ? console : new com.justsyncit.ConsoleInfoDisplay();
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
        console.displayError("Error: Missing subcommand");
        displayAvailableSubcommands();
    }

    /**
     * Displays error message for unknown subcommand.
     *
     * @param subcommand the unknown subcommand
     */
    private void displayUnknownSubcommandError(String subcommand) {
        console.displayError("Error: Unknown subcommand: " + subcommand);
        displayAvailableSubcommands();
    }

    /**
     * Displays the list of available subcommands.
     */
    private void displayAvailableSubcommands() {
        console.displayError("Available subcommands: " + SUBCOMMAND_START + ", "
                + SUBCOMMAND_STOP + ", " + SUBCOMMAND_STATUS);
        console.displayError("Use 'help server' for more information");
    }

    /**
     * Displays detailed help information for the server command group.
     */
    private void displayHelp() {
        console.displayInfo("Server Command Group Help");
        console.displayInfo("===========================");
        console.displayInfo("");
        console.displayInfo("Usage: " + getUsage());
        console.displayInfo("");
        console.displayInfo("Description:");
        console.displayInfo("  " + getDescription());
        console.displayInfo("");
        console.displayInfo("Subcommands:");
        console.displayInfo("  " + SUBCOMMAND_START + "        Start a backup server");
        console.displayInfo("  " + SUBCOMMAND_STOP + "         Stop a running backup server");
        console.displayInfo("  " + SUBCOMMAND_STATUS + "       Show server status and configuration");
        console.displayInfo("");
        console.displayInfo("Examples:");
        console.displayInfo("  server " + SUBCOMMAND_START);
        console.displayInfo("  server " + SUBCOMMAND_STOP);
        console.displayInfo("  server " + SUBCOMMAND_STATUS);
        console.displayInfo("");
        console.displayInfo("For detailed help on a specific subcommand, use:");
        console.displayInfo("  help server " + SUBCOMMAND_START);
        console.displayInfo("  help server " + SUBCOMMAND_STOP);
        console.displayInfo("  help server " + SUBCOMMAND_STATUS);
    }
}