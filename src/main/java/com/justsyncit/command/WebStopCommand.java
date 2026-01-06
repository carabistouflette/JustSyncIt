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
