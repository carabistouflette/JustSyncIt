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
 * Interface for command implementations.
 * Follows Open/Closed Principle by allowing new commands to be added without
 * modifying existing code.
 *
 * <p>
 * Commands are responsible for:
 * </p>
 * <ul>
 * <li>Parsing and validating their own arguments</li>
 * <li>Executing their specific functionality</li>
 * <li>Providing user-friendly help and usage information</li>
 * <li>Returning success/failure status</li>
 * </ul>
 *
 * <p>
 * Implementations should follow these conventions:
 * </p>
 * <ul>
 * <li>Use System.out for normal output</li>
 * <li>Use System.err for error messages</li>
 * <li>Return true on success, false on failure</li>
 * <li>Handle --help option to display detailed help</li>
 * <li>Avoid System.exit() calls</li>
 * </ul>
 */
public interface Command {

    /**
     * Executes the command with the provided arguments.
     *
     * <p>
     * The command should:
     * </p>
     * <ul>
     * <li>Parse and validate the arguments</li>
     * <li>Check for the --help option and display help if present</li>
     * <li>Execute the command's functionality</li>
     * <li>Report errors to System.err</li>
     * <li>Return true on success, false on failure</li>
     * </ul>
     *
     * @param args    the command arguments (not including the command name itself)
     * @param context the execution context providing access to shared services
     * @return true if the command was executed successfully, false otherwise
     */
    boolean execute(String[] args, CommandContext context);

    /**
     * Gets the name of the command.
     *
     * <p>
     * The name should be:
     * </p>
     * <ul>
     * <li>Lowercase</li>
     * <li>Unique within the command registry</li>
     * <li>Short and memorable</li>
     * </ul>
     *
     * @return the command name (never null or empty)
     */
    String getName();

    /**
     * Gets the description of the command.
     *
     * <p>
     * The description should be a brief, single-line summary of what the command
     * does.
     * </p>
     *
     * @return the command description (never null or empty)
     */
    String getDescription();

    /**
     * Gets the usage information for the command.
     *
     * <p>
     * The usage should show the command syntax with required and optional
     * arguments.
     * Use angle brackets for required arguments (&lt;arg&gt;) and square brackets
     * for
     * optional arguments [--option].
     * </p>
     *
     * <p>
     * Example: "backup &lt;source-dir&gt; [--remote] [--server host:port]"
     * </p>
     *
     * @return the usage string (never null or empty)
     */
    String getUsage();

    /**
     * Checks if the help option is present in the arguments.
     * This is a convenience method that implementations can use.
     *
     * @param args the command arguments
     * @return true if --help is present in the arguments
     */
    default boolean isHelpRequested(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates that the minimum number of arguments are provided.
     * This is a convenience method that implementations can use.
     *
     * @param args         the command arguments
     * @param minArgs      the minimum number of required arguments
     * @param errorMessage the error message to display if validation fails
     * @return true if validation passes, false otherwise
     */
    default boolean validateMinArgs(String[] args, int minArgs, String errorMessage) {
        if (args == null || args.length < minArgs) {
            System.err.println("Error: " + errorMessage);
            System.err.println(getUsage());
            return false;
        }
        return true;
    }

    /**
     * Standardized error handling method for commands.
     * Unwraps CompletionException/ExecutionException and logs appropriately.
     *
     * @param message User-facing error message prefix
     * @param e       The exception that occurred
     * @param logger  The SLF4J logger to use
     */
    default void handleError(String message, Throwable e, org.slf4j.Logger logger) {
        Throwable cause = e;
        // Unwrap concurrency exceptions to get to the real error
        while ((cause instanceof java.util.concurrent.CompletionException ||
                cause instanceof java.util.concurrent.ExecutionException) &&
                cause.getCause() != null) {
            cause = cause.getCause();
        }

        if (logger != null) {
            logger.error(message, cause);
        }
        System.err.println(message + ": " + cause.getMessage());
    }

    /**
     * Displays a standardized error message for unknown options.
     * This is a convenience method that implementations can use.
     *
     * @param option the unknown option
     */
    default void displayUnknownOptionError(String option) {
        System.err.println("Error: Unknown option: " + option);
        System.err.println(getUsage());
        System.err.println("Use '" + getName() + " --help' for more information");
    }
}