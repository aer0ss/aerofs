/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.Loggers;
import com.aerofs.lib.properties.Configuration;

public class DeviceManagement
{
    public static void main(String args[])
            throws Exception
    {
        if (args.length != 1) {
            System.err.println("usage: <prog_name> <devman_yml_filename>");
            System.exit(1);
        }

        // Init the logger.
        Loggers.init();

        // Init the catch-all exception handler
        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());

        // Init the configuration service.
        Configuration.Server.initialize();

        // Run the device management service.
        DeviceManagementService service = new DeviceManagementService();
        service.run(new String[]{"server", args[0]});
    }
}
