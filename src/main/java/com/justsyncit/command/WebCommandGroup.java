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

import com.justsyncit.web.WebServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command group for web-related commands.
 */
public final class WebCommandGroup implements Command {

    private static final Logger logger = LoggerFactory.getLogger(WebCommandGroup.class);

    private final Map<String, Command> subCommands = new HashMap<>();

    public WebCommandGroup() {
        // Register subcommands
        subCommands.put("start", new WebStartCommand());
        subCommands.put("stop", new WebStopCommand());
    }

    @Override
    public String getName() {
        return "web";
    }

    @Override
    public String getDescription() {
        return "Web interface management commands";
    }

    @Override
    public String getUsage() {
        return "web <command> [options]\n" +
                "\nCommands:\n" +
                "  start [--port <port>]  Start the web interface (default port: 8080)\n" +
                "  stop                   Stop the web interface\n" +
                "  status                 Show web interface status";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (isHelpRequested(args)) {
            System.out.println(getUsage());
            return true;
        }

        if (args.length == 0) {
            printStatus();
            System.out.println("\n" + getUsage());
            return true;
        }

        String subCommand = args[0];

        // Handle status as a special case
        if ("status".equals(subCommand)) {
            printStatus();
            return true;
        }

        // Look up the subcommand
        Command cmd = subCommands.get(subCommand);
        if (cmd == null) {
            logger.error("Unknown web command: {}", subCommand);
            System.err.println("Unknown web command: " + subCommand);
            System.out.println(getUsage());
            return false;
        }

        // Pass remaining args to subcommand
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        return cmd.execute(subArgs, context);
    }

    private void printStatus() {
        WebServer server = WebStartCommand.getRunningServer();
        if (server != null && server.isRunning()) {
            System.out.println("Web Interface Status: RUNNING");
            System.out.println("  URL: http://localhost:" + server.getPort());
        } else {
            System.out.println("Web Interface Status: STOPPED");
            System.out.println("  Run 'justsyncit web start' to start the web interface");
        }
    }
}
