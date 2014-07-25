/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.support.tools;

import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.lib.properties.Configuration;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.protobuf.ByteString;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newOneWayAuthClientFactory;

public class DeactivateUser
{
    public static void main(String args[])
            throws Exception
    {
        LogUtil.setLevel(Level.NONE);
        Configuration.Server.initialize();

        if (args.length != 2) {
            System.err.println("Usage: java -jar support-deactivate-user.jar <user_id> <password>");
            System.exit(1);
        }

        SPBlockingClient sp = newOneWayAuthClientFactory().create();
        sp.credentialSignIn(args[0], ByteString.copyFrom(args[1].getBytes()));

        // Deactivate the user. Do not erase devices.
        sp.deactivateUser(args[0], false);
    }
}