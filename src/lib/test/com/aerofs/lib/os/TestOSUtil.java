/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.os;

import com.aerofs.lib.os.OSUtil.OSFamily;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestOSUtil
{
    @Test
    public void shouldReturnCorrectStrings()
    {
        // Since these strings are displayed to end users (DeviceRegistrationEmailer.java) and are
        // used in devices.mako to determine the icons, we shouldn't change them.
        // Also see sp.proto:RegisterDeviceCall.os_family.
        assertEquals(OSFamily.LINUX.getString(), "Linux");
        assertEquals(OSFamily.WINDOWS.getString(), "Windows");
        assertEquals(OSFamily.OSX.getString(), "Mac OS X");
    }

    @Test
    public void shouldReplaceEnvironmentVariables()
    {
        String home = System.getenv(OSUtil.PROPERTY_HOME);

        // N.B. paths[i] _must_ correspond to expected[i] for all 0 <= i < paths.length.
        String[] paths = {
                "~",
                "~/ux/environment",
                "${HOME}/ux/environment",
                "ux/${HOME}/environment",
                "ux/environment/${HOME}",
                "no/environment/variables",
                "~\\windows\\environment",
                "${HOME}\\windows\\environment",
                "windows\\${HOME}\\environment",
                "windows\\environment\\${HOME}",
                "C:\\windows\\environment\\${HOME}",
                "no\\environment\\variables",
                "C:\\no\\environment\\variables"
        };
        String[] expected = {
                home,
                home + "/ux/environment",
                home + "/ux/environment",
                "ux/" + home + "/environment",
                "ux/environment/" + home,
                "no/environment/variables",
                home + "\\windows\\environment",
                home + "\\windows\\environment",
                "windows\\" + home + "\\environment",
                "windows\\environment\\" + home,
                "C:\\windows\\environment\\" + home,
                "no\\environment\\variables",
                "C:\\no\\environment\\variables"
        };

        for (int i = 0; i < paths.length; i++)
        {
            assertEquals(expected[i], OSUtil.replaceEnvironmentVariables(paths[i]));
        }
    }
}
