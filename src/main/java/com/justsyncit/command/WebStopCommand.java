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

/**
 * Command to stop the running web server.
 * Usage: justsyncit web stop
 */
public final class WebStopCommand implements Command {

    @Override
    public String getName() {
        return "web stop";
    }

    @Override
    public String getDescription() {
        return "Stop the web-based management interface";
    }

    @Override
    public String getUsage() {
        return "web stop";
    }

    @Override
    public boolean execute(String[] args, CommandContext context) {
        if (isHelpRequested(args)) {
            System.out.println(getUsage());
            return true;
        }

        WebServer server = WebStartCommand.getRunningServer();

        if (server == null || !server.isRunning()) {
            System.out.println("Web server is not running");
            return true;
        }

        server.stop();
        System.out.println("Web server stopped");
        return true;
    }
}
