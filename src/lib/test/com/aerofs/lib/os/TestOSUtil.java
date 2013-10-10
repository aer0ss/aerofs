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
}
