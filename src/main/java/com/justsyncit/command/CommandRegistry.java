package com.justsyncit.command;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for managing commands.
 * Follows Open/Closed Principle by allowing new commands to be registered
 * without modification.
 */
public final class CommandRegistry {

    /** Map of registered commands. */
    private final Map<String, Command> commands = new HashMap<>();

    /**
     * Initializes the command registry with default commands.
     */
    public CommandRegistry() {
        // Register existing commands
        register(new com.justsyncit.command.HashCommand(null)); // Will be injected properly
        register(new com.justsyncit.command.VerifyCommand()); // Uses CommandContext for injection
        register(new com.justsyncit.command.BackupCommand(null)); // Will be injected properly
        register(new com.justsyncit.command.RestoreCommand(null)); // Will be injected properly

        // Register new snapshot management commands
        register(new SnapshotsCommandGroup());

        // Register new network operation commands
        register(new ServerCommandGroup());
        register(new com.justsyncit.command.TransferCommand(null)); // Will be injected properly
        register(new com.justsyncit.command.SyncCommand(null)); // Will be injected properly

        // Register web management command
        register(new WebCommandGroup());

        // Register search command
        register(new SearchCommand());
    }

    /**
     * Registers a command with the registry.
     *
     * @param command the command to register
     */
    public void register(Command command) {
        commands.put(command.getName(), command);
    }

    /**
     * Gets a command by name.
     *
     * @param name the command name
     * @return the command, or null if not found
     */
    public Command getCommand(String name) {
        return commands.get(name);
    }

    /**
     * Checks if a command is registered.
     *
     * @param name the command name
     * @return true if the command is registered, false otherwise
     */
    public boolean hasCommand(String name) {
        return commands.containsKey(name);
    }

    /**
     * Gets all registered commands.
     *
     * @return array of all registered commands
     */
    public Command[] getAllCommands() {
        return commands.values().toArray(new Command[0]);
    }

    /**
     * Displays help information for all commands.
     */
    public void displayHelp() {
        System.out.println("Available commands:");
        for (Command command : commands.values()) {
            System.out.println("  " + command.getUsage() + " - " + command.getDescription());
        }
    }
}