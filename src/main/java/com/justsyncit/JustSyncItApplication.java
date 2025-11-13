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

/**
 * Refactored main application class that follows SOLID principles.
 * This class focuses only on application lifecycle and command orchestration.
 */
public class JustSyncItApplication {

    private static final Logger logger = LoggerFactory.getLogger(JustSyncItApplication.class);
    
    private final Blake3Service blake3Service;
    private final CommandRegistry commandRegistry;
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
        logger.info("JustSyncIt is running");
        
        // Display BLAKE3 implementation info
        infoDisplay.displayBlake3Info(blake3Service);

        // Display application header
        System.out.println("JustSyncIt - Backup Solution");
        System.out.println("Version: 1.0-SNAPSHOT");

        if (args.length == 0) {
            logger.info("Running with no arguments");
            displayUsage();
            return;
        }

        // Process commands
        processCommands(args);
    }

    /**
     * Processes command line arguments.
     *
     * @param args command line arguments
     */
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
            System.out.println("No valid command found. Use --help for available commands.");
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
        System.out.println("Usage: java -jar JustSyncIt.jar [options]");
        commandRegistry.displayHelp();
    }
}