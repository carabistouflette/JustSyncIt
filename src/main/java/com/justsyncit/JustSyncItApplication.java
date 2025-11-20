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

package com.justsyncit;

import com.justsyncit.command.Command;
import com.justsyncit.command.CommandContext;
import com.justsyncit.command.CommandRegistry;
import com.justsyncit.hash.Blake3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Refactored main application class that follows SOLID principles.
 * This class focuses only on application lifecycle and command orchestration.
 */
public class JustSyncItApplication {

    /** Logger for the application. */
    private static final Logger logger = LoggerFactory.getLogger(JustSyncItApplication.class);

    /** BLAKE3 service instance. */
    private final Blake3Service blake3Service;

    /** Command registry for managing commands. */
    private final CommandRegistry commandRegistry;

    /** Application info display component. */
    private final ApplicationInfoDisplay infoDisplay;

    /**
     * Creates a new JustSyncItApplicationRefactored with all dependencies.
     *
     * @param blake3Service the BLAKE3 service
     * @param commandRegistry the command registry
     * @param infoDisplay the application info display
     */
    public JustSyncItApplication(
            Blake3Service blake3Service,
            CommandRegistry commandRegistry,
            ApplicationInfoDisplay infoDisplay) {
        this.blake3Service = blake3Service;
        this.commandRegistry = commandRegistry;
        this.infoDisplay = infoDisplay;
    }

    /**
     * Main entry point for the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        logger.info("Starting JustSyncIt backup solution");

        try {
            // Create dependencies (in a real application, this would use DI framework)
            ServiceFactory serviceFactory = new ServiceFactory();
            JustSyncItApplication app = serviceFactory.createApplication();
            app.run(args);
        } catch (Exception e) {
            logger.error("Application failed to start", e);
            System.exit(1);
        }
    }

    /**
     * Runs the application with the provided arguments.
     *
     * @param args command line arguments
     */
    public void run(String[] args) {
        // Process logging options first
        String[] processedArgs = processLoggingOptions(args);

        logger.info("JustSyncIt is running");

        // Display BLAKE3 implementation info
        infoDisplay.displayBlake3Info(blake3Service);

        // Display application header
        logger.info("JustSyncIt - Backup Solution");
        logger.info("Version: 1.0-SNAPSHOT");

        if (processedArgs.length == 0) {
            logger.info("Running with no arguments");
            displayUsage();
            return;
        }

        // Process commands
        processCommands(processedArgs);
    }

    /**
     * Processes command line arguments.
     *
     * @param args command line arguments
     */
    /**
     * Processes logging options and returns remaining arguments.
     *
     * @param args command line arguments
     * @return arguments without logging options
     */
    private String[] processLoggingOptions(String[] args) {
        String logLevel = null;
        String logbackConfig = null;
        boolean verbose = false;
        boolean quiet = false;

        // First pass: identify logging options
        for (String arg : args) {
            if (arg.equals("--verbose")) {
                verbose = true;
            } else if (arg.equals("--quiet")) {
                quiet = true;
            } else if (arg.startsWith("--log-level=")) {
                logLevel = arg.substring("--log-level=".length());
            }
        }

        // Determine logging configuration
        if (verbose) {
            logbackConfig = "logback-verbose.xml";
        } else if (quiet) {
            logbackConfig = "logback-quiet.xml";
        }

        // Apply logging configuration
        if (logbackConfig != null) {
            applyLogbackConfiguration(logbackConfig);
        }

        // Apply log level if specified
        if (logLevel != null) {
            applyLogLevel(logLevel);
        }

        // Filter out logging options from arguments
        return filterLoggingOptions(args);
    }

    /**
     * Applies a Logback configuration file.
     *
     * @param configFilename the configuration filename
     */
    private void applyLogbackConfiguration(String configFilename) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.reset();

            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(loggerContext);

            InputStream configStream = getClass().getClassLoader().getResourceAsStream(configFilename);
            if (configStream != null) {
                configurator.doConfigure(configStream);
                logger.info("Applied logging configuration: {}", configFilename);
            } else {
                logger.warn("Logging configuration file not found: {}", configFilename);
            }
        } catch (JoranException e) {
            logger.error("Failed to apply logging configuration: {}", configFilename, e);
        }
    }

    /**
     * Applies a specific log level to the root logger.
     *
     * @param levelName the log level name
     */
    private void applyLogLevel(String levelName) {
        try {
            Level level = Level.toLevel(levelName.toUpperCase(Locale.ROOT), Level.INFO);
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.getLogger("ROOT").setLevel(level);
            logger.info("Applied log level: {}", level);
        } catch (Exception e) {
            logger.error("Failed to apply log level: {}", levelName, e);
        }
    }

    /**
     * Filters out logging options from arguments.
     *
     * @param args original arguments
     * @return arguments without logging options
     */
    private String[] filterLoggingOptions(String[] args) {
        java.util.List<String> filteredArgs = new java.util.ArrayList<>();

        for (String arg : args) {
            if (!arg.equals("--verbose")
                    && !arg.equals("--quiet")
                    && !arg.startsWith("--log-level=")) {
                filteredArgs.add(arg);
            }
        }

        return filteredArgs.toArray(new String[0]);
    }

    private void processCommands(String[] args) {
        logger.info("Running with {} arguments", args.length);

        CommandContext context = new CommandContext(blake3Service);
        boolean commandExecuted = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (commandRegistry.hasCommand(arg)) {
                Command command = commandRegistry.getCommand(arg);

                // Extract arguments for this command
                String[] commandArgs = extractCommandArgs(args, i, command);

                boolean success = command.execute(commandArgs, context);
                if (success) {
                    commandExecuted = true;
                }

                // Skip past command arguments
                i += commandArgs.length;
            }
        }

        if (!commandExecuted) {
            logger.info("No valid command found. Use --help for available commands.");
            commandRegistry.displayHelp();
        }
    }

    /**
     * Extracts arguments for a specific command.
     *
     * @param args all arguments
     * @param startIndex starting index of command
     * @param command the command being executed
     * @return array of arguments for the command
     */
    private String[] extractCommandArgs(String[] args, int startIndex, Command command) {
        String commandName = command.getName();
        int argCount = getExpectedArgCount(commandName);

        if (startIndex + argCount >= args.length) {
            return new String[0];
        }

        String[] commandArgs = new String[argCount + 1]; // +1 for command name
        commandArgs[0] = commandName;
        System.arraycopy(args, startIndex + 1, commandArgs, 1, argCount);

        return commandArgs;
    }

    /**
     * Gets the expected number of arguments for a command.
     *
     * @param commandName the command name
     * @return expected argument count
     */
    private int getExpectedArgCount(String commandName) {
        switch (commandName) {
            case "--hash":
                return 1; // file path
            case "--verify":
                return 2; // file path and hash
            default:
                return 0;
        }
    }

    /**
     * Displays usage information.
     */
    private void displayUsage() {
        logger.info("Usage: java -jar JustSyncIt.jar [options]");
        logger.info("Logging options:");
        logger.info("  --verbose              Enable verbose logging (DEBUG level)");
        logger.info("  --quiet                Enable quiet logging (WARN level only)");
        logger.info("  --log-level=<LEVEL>    Set specific log level (TRACE, DEBUG, INFO, WARN, ERROR)");
        logger.info("");
        commandRegistry.displayHelp();
    }
}