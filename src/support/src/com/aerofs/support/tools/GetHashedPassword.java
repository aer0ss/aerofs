/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.support.tools;

import com.aerofs.base.Base64;
import com.aerofs.ids.UserID;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.lib.properties.Configuration;
import com.aerofs.sp.authentication.LocalCredential;

public class GetHashedPassword
{
    public static void main(String args[])
            throws Exception
    {
        LogUtil.setLevel(Level.NONE);
        Configuration.Server.initialize();

        if (args.length != 2) {
            System.err.println("Usage: java -jar get-hashed-password.jar <user_id> <password>");
            System.exit(1);
        }

        String hashedPassword = Base64.encodeBytes(LocalCredential.hashScrypted(
            LocalCredential.deriveKeyForUser(UserID.fromExternal(args[0]),
                    args[1].getBytes())));

        System.out.println(hashedPassword);
    }
}