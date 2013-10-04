/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.os;

import com.aerofs.lib.os.OSUtil.OSFamily;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import static com.google.common.base.Preconditions.checkArgument;
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

        String[] input = {
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

        verify(expected, input, null);
    }

    @Test
    public void shouldReplaceUsingAdditionalEnvironmentVariables()
    {
        ImmutableMap<String, String> additionalVars =
                ImmutableMap.of("EXTRA", "extra", "ADDITIONAL", "additional");

        String[] input = {
                "ux/${EXTRA}",
                "ux/${EXTRA}/${ADDITIONAL}",
                "windows\\${ADDITIONAL}\\environment",
                "windows\\${EXTRA}",
                "windows\\${ADDITIONAL}\\${EXTRA}"
        };

        String[] expected = {
                "ux/extra",
                "ux/extra/additional",
                "windows\\additional\\environment",
                "windows\\extra",
                "windows\\additional\\extra"
        };

        verify(expected, input, additionalVars);
    }

    @Test
    public void shouldPreferAdditionalEnvironmentVariablesOverSystemEnvironmentVariables()
    {
        ImmutableMap<String, String> additionalVars = ImmutableMap.of(OSUtil.PROPERTY_HOME, "home");

        String[] input = {
                "~",
                "~/ux/environment",
                "ux/${" + OSUtil.PROPERTY_HOME + "}/environment",
                "windows\\${" + OSUtil.PROPERTY_HOME + "}\\environment"
        };

        String[] expected = {
                "home",
                "home/ux/environment",
                "ux/home/environment",
                "windows\\home\\environment"
        };

        verify(expected, input, additionalVars);
    }

    /**
     * @pre expected.length == input.length
     */
    private void verify(String[] expected, String[] input,
            ImmutableMap<String, String> additionalVars)
    {
        checkArgument(expected.length == input.length);

        for (int i = 0; i < input.length; i++) {
            assertEquals(expected[i], OSUtil.replaceEnvironmentVariables(input[i], additionalVars));
        }
    }
}
