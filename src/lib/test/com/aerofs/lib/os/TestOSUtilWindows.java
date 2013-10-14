/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.os;

import com.google.common.collect.ImmutableMap;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@Ignore
@RunWith(PowerMockRunner.class)
@PrepareForTest({OSUtil.class, OSUtilWindows.class})
@PowerMockIgnore({"ch.qos.logback.*", "org.slf4j.*"})
public class TestOSUtilWindows
{
    private OSUtilWindows _util = new OSUtilWindows();

    @Test
    public void shouldDisallowInvalidFiles()
    {
        String[] IllegalNames = {
                "con", "CON", "PRN", "AUX",
                "NUL", "COM1", "COM9", "LPT1",
                "LPT9", "foo<", "foo>",
                "CLOCK$",
                "foo:", "foo\"", "foo/",
                "foo\\", "foo|", "foo?", "foo*",
                "...", "....", ".....",
                "LPT1.txt", "CON.whatever", "nul.something"
        };

        for (String s : IllegalNames) {
            assertFalse("Should be illegal: " + s, OSUtilWindows.isValidFileName(s));
        }
    }
    @Test
    public void shouldAllowValidFiles()
    {
        String[] LegalNames = {
                "_con", ".con", " con",
                "...con.txt",
                "foo,", "lpt0",
                "CLOCK", "CLOCK.x", "CLOCK1", ".CLOCK$",
                "null", "null.nul", "...foo.foo",
                ".txt", "...txt"
        };

        for (String s : LegalNames) {
            assertTrue("Should be legal: " + s, OSUtilWindows.isValidFileName(s));
        }
    }

    @Test
    public void shouldSubstituteEnvironmentVariable() throws Exception
    {
        mockWindowsXP();

        assertEquals("C:\\Documents and Settings\\User\\AeroFS",
                _util.replaceEnvironmentVariables("${USERPROFILE}\\AeroFS"));
        assertEquals("C:\\Documents and Settings\\User\\Application Data\\AeroFS",
                _util.replaceEnvironmentVariables("${APPDATA}\\AeroFS"));
    }

    @Test
    public void shouldSubstituteLocalAppDataEvenWhenUndefined() throws Exception
    {
        mockWindowsXP();

        assertEquals("C:\\Documents and Settings\\User\\Local Settings\\Application Data\\AeroFS",
                _util.replaceEnvironmentVariables("${LOCALAPPDATA}\\AeroFS"));
    }

    @Test
    public void shouldLeaveUnresolvedVariableAsIs() throws Exception
    {
        mockWindowsXP();

        assertEquals("${WHATISLOVE}\\AeroFS",
                _util.replaceEnvironmentVariables("${WHATISLOVE}\\AeroFS"));
        assertEquals("C:\\Documents and Settings\\User\\${WHATISLOVE}\\AeroFS",
                _util.replaceEnvironmentVariables("${USERPROFILE}\\${WHATISLOVE}\\AeroFS"));
    }

    @Test
    public void shouldNotResolveTilde() throws Exception
    {
        mockWindowsXP();

        assertEquals("~", _util.replaceEnvironmentVariables("~"));
    }

    // N.B. this method mocks just enough environment settings that we rely on for the internal
    //   logic of OSUtilWindows to work.
    private void mockWindowsXP() throws Exception
    {
        mockStatic(System.class);
        when(System.getProperty("os.name")).thenReturn("Windows XP");
        when(System.getProperty("os.arch")).thenReturn("x86");
        when(System.getenv("APPDATA")).thenReturn("C:\\Documents and Settings\\User\\Application Data");
        when(System.getenv("USERPROFILE")).thenReturn("C:\\Documents and Settings\\User");
        when(System.getenv()).thenReturn(ImmutableMap.of(
                "APPDATA", "C:\\Documents and Settings\\User\\Application Data",
                "USERPROFILE", "C:\\Documents and Settings\\User"
        ));

        File mockLocalAppData = PowerMockito.mock(File.class);

        mockStatic(File.class);
        whenNew(File.class)
                .withArguments("C:\\Documents and Settings\\User\\Local Settings\\Application Data")
                .thenReturn(mockLocalAppData);
        when(mockLocalAppData.isDirectory()).thenReturn(true);
    }
}
