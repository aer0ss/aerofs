/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui;

import com.aerofs.lib.OutArg;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;

import java.io.IOException;

/**
 * On Ubuntu with Unity, tray icons are blacklisted by default. This class checks if aerofs is
 * on the whitelist and adds it if necessary. It will then prompt the user to log out and log
 * back in.
 */
public class UbuntuTraySettings
{
    static void checkAndUpdateUbuntuTraySettings()
    {
        assert OSUtil.isLinux();

        final String appName = "'swt'"; // This is how Ubuntu thinks AeroFS is named
        final String pattern = ".*(" + appName + "|'all').*";

        try {
            String settings = getUbuntuTraySettings();
            if (!settings.matches(pattern)) {
                settings += (settings.isEmpty() ? "" : ", ") + appName;
                OutArg<String> outArg = new OutArg<String>();
                int retval = Util.execForeground(outArg, "gsettings", "set",
                        "com.canonical.Unity.Panel", "systray-whitelist", "[" + settings + "]");
                if (retval != 0) throw new IOException("gsettings returned " + outArg.get());

                // Check if we match the settings now, otherwise something is wrong with our logic
                // and there is no need to prompt the user to log out.
                if (!getUbuntuTraySettings().matches(pattern)) {
                    throw new IOException("nothing written");
                }

                GUI.get().show(MessageType.INFO,
                        S.PRODUCT + " has updated your system settings to allow " +
                        "displaying the " + S.PRODUCT +
                        " icon in the tray menu. If you don't see " +
                        "the tray icon, please log out of your session and log back in.\n\n" +
                        "Alternatively, you can use the 'aerofs-sh' command to use " +
                        S.PRODUCT +
                        " from the command line.");
            }
        } catch (IOException e) {
            Util.l(UbuntuTraySettings.class).warn("gsettings failed: " + Util.e(e));
        }
    }

    static private String getUbuntuTraySettings() throws IOException
    {
        assert OSUtil.isLinux();

        OutArg<String> outArg = new OutArg<String>();
        Util.execForeground(outArg, "gsettings", "get", "com.canonical.Unity.Panel",
                "systray-whitelist");
        String result = outArg.get().trim();
        if (result.startsWith("[") && result.endsWith("]")) {
            return result.substring(1, result.length() - 1).trim();
        } else if (result.equals("@as []")) { // empty array
            return "";
        } else {
            throw new IOException("gsettings returned: " + result);
        }
    }
}