/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.os;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static com.aerofs.lib.os.AbstractOSUtilLinuxOSX.replaceEnvironmentVariables;
import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OSUtil.class, OSUtilOSX.class})
public class TestAbstractOSUtilLinuxOSX
{
    @Test
    public void shouldSubstituteEnvironmentVariable() throws Exception
    {
        mockOSX();

        assertEquals("/Users/user//Users/root/AeroFS",
                replaceEnvironmentVariables("${HOME}/${MY_HOME}/AeroFS"));
        assertEquals("/Users/root/AeroFS", replaceEnvironmentVariables("${MY_HOME}/AeroFS"));
    }

    @Test
    public void shouldLeaveUnresolvedVariableAsIs() throws Exception
    {
        mockOSX();

        assertEquals("${WHATISLOVE}", replaceEnvironmentVariables("${WHATISLOVE}"));
        assertEquals("/Users/user/${WHATISLOVE}/AeroFS",
                replaceEnvironmentVariables("${HOME}/${WHATISLOVE}/AeroFS"));
    }

    @Test
    public void shouldSubstituteTilde() throws Exception
    {
        mockOSX();

        assertEquals("/Users/userhome/AeroFS", replaceEnvironmentVariables("~/AeroFS"));
        assertEquals("/Users/userhome//Users/user/AeroFS",
                replaceEnvironmentVariables("~/${HOME}/AeroFS"));
    }

    // N.B. this method mocks just enough environment settings that we rely on for the internal
    //   logic of OSUtilOSX to work.
    private void mockOSX() throws Exception
    {
        mockStatic(System.class);
        when(System.getProperty("user.home")).thenReturn("/Users/userhome");
        when(System.getProperty("os.name")).thenReturn("Mac OS X");
        when(System.getProperty("os.arch")).thenReturn("x86_64");
        when(System.getenv("HOME")).thenReturn("/Users/user");
        when(System.getenv("MY_HOME")).thenReturn("/Users/root");
        when(System.getenv()).thenReturn(ImmutableMap.of(
                "HOME", "/Users/user",
                "MY_HOME", "/Users/root"));
    }
}
