/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server;

import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;

import java.util.Properties;

public class CommandServer
{
    public static void main(String args[])
            throws Exception
    {
        if (args.length != 1) {
            System.err.println("usage: <prog_name> <command_yml_filename>");
            System.exit(1);
        }

        // Init the logger.
        Loggers.init();

        // init catch-all exception handler
        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());

        // Make an empty Properties set so the verkehr config client won't NPE
        ConfigurationProperties.setProperties(new Properties());
        System.err.println("starting up");

        // Run the command server service.
        CommandServerService service = new CommandServerService();
        service.run(new String[]{"server", args[0]});
    }
}
