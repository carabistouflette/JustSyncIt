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

package com.justSyncIt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class for JustSyncIt backup solution.
 */
public class JustSyncItApplication {

    private static final Logger logger = LoggerFactory.getLogger(JustSyncItApplication.class);

    /**
     * Main entry point for the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        logger.info("Starting JustSyncIt backup solution");

        try {
            JustSyncItApplication app = new JustSyncItApplication();
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

        // FIXME: Implement main application logic
        System.out.println("JustSyncIt - Backup Solution");
        System.out.println("Version: 1.0-SNAPSHOT");

        if (args.length > 0) {
            logger.info("Running with {} arguments", args.length);
            for (int i = 0; i < args.length; i++) {
                System.out.println("Arg " + i + ": " + args[i]);
            }
        } else {
            logger.info("Running with no arguments");
            System.out.println("Usage: java -jar JustSyncIt.jar [options]");
        }
    }
}