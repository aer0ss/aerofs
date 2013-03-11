/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.aerofs.base.Loggers;

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

        // Run the device management service.
        DeviceManagementService service = new DeviceManagementService();
        service.run(new String[]{"server", args[0]});
    }
}
