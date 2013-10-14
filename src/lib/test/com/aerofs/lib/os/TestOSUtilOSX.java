/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.os;

import com.aerofs.lib.injectable.InjectableFile.Factory;
import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@Ignore
@RunWith(PowerMockRunner.class)
@PrepareForTest({OSUtil.class, OSUtilOSX.class})
@PowerMockIgnore({"ch.qos.logback.*", "org.slf4j.*"})
public class TestOSUtilOSX
{
    final private OSUtilOSX _util = new OSUtilOSX(new Factory());

    @Test
    public void shouldSubstituteEnvironmentVariable() throws Exception
    {
        mockOSX();

        Assert.assertEquals("/Users/user//Users/root/AeroFS",
                _util.replaceEnvironmentVariables("${HOME}/${MY_HOME}/AeroFS"));
        Assert.assertEquals("/Users/root/AeroFS",
                _util.replaceEnvironmentVariables("${MY_HOME}/AeroFS"));
    }

    @Test
    public void shouldLeaveUnresolvedVariableAsIs() throws Exception
    {
        mockOSX();

        Assert.assertEquals("${WHATISLOVE}",
                _util.replaceEnvironmentVariables("${WHATISLOVE}"));
        Assert.assertEquals("/Users/user/${WHATISLOVE}/AeroFS",
                _util.replaceEnvironmentVariables("${HOME}/${WHATISLOVE}/AeroFS"));
    }

    @Test
    public void shouldSubstituteTilde() throws Exception
    {
        mockOSX();

        Assert.assertEquals("/Users/userhome/AeroFS",
                _util.replaceEnvironmentVariables("~/AeroFS"));
        Assert.assertEquals("/Users/userhome//Users/user/AeroFS",
                _util.replaceEnvironmentVariables("~/${HOME}/AeroFS"));
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
