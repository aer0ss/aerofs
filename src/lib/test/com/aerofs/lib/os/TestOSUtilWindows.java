/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.os;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import static com.aerofs.lib.os.OSUtilWindows.replaceEnvironmentVariables;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestOSUtilWindows
{
    static final String LARGER_THAN_MAXPATH = Strings.repeat("x", 256);

    @Test
    public void shouldDisallowInvalidFiles()
    {
        String[] IllegalNames = {
                "foo<", "foo>", "foo:", "foo\"", "foo/",
                "foo\\", "foo|", "foo?", "foo*",
                LARGER_THAN_MAXPATH
        };

        for (String s : IllegalNames) {
            assertTrue("Should be illegal: " + s, OSUtilWindows.isInvalidWin32FileName(s));
        }
    }

    @Test
    public void shouldAllowValidFiles()
    {
        String[] LegalNames = {
                "con", "CON", "PRN", "AUX",
                "NUL", "COM1", "COM9", "LPT1",
                "LPT9",
                "CLOCK$",
                "a.", "...", "....", ".....",
                "a ", "   ", "    ", "     ",
                "LPT1.txt", "CON.whatever", "nul.something",
                "_con", ".con", " con",
                "...con.txt",
                "foo,", "lpt0",
                "CLOCK", "CLOCK.x", "CLOCK1", ".CLOCK$",
                "null", "null.nul", "...foo.foo",
                ".txt", "...txt"
        };

        for (String s : LegalNames) {
            assertFalse("Should be legal: " + s, OSUtilWindows.isInvalidWin32FileName(s));
        }
    }

    // powermock doesn't like java 8
    @Ignore
    @Test
    public void shouldSubstituteEnvironmentVariable() throws Exception
    {
        mockWindowsXP();

        assertEquals("C:\\Documents and Settings\\User\\AeroFS",
                replaceEnvironmentVariables("${USERPROFILE}\\AeroFS"));
        assertEquals("C:\\Documents and Settings\\User\\Application Data\\AeroFS",
                replaceEnvironmentVariables("${APPDATA}\\AeroFS"));
    }

    // powermock doesn't like java 8
    @Ignore
    @Test
    public void shouldSubstituteLocalAppDataEvenWhenUndefined() throws Exception
    {
        mockWindowsXP();

        assertEquals("C:\\Documents and Settings\\User\\Local Settings\\Application Data\\AeroFS",
                replaceEnvironmentVariables("${LOCALAPPDATA}\\AeroFS"));
    }

    // powermock doesn't like java 8
    @Ignore
    @Test
    public void shouldLeaveUnresolvedVariableAsIs() throws Exception
    {
        mockWindowsXP();

        assertEquals("${WHATISLOVE}\\AeroFS",
                replaceEnvironmentVariables("${WHATISLOVE}\\AeroFS"));
        assertEquals("C:\\Documents and Settings\\User\\${WHATISLOVE}\\AeroFS",
                replaceEnvironmentVariables("${USERPROFILE}\\${WHATISLOVE}\\AeroFS"));
    }

    // powermock doesn't like java 8
    @Ignore
    @Test
    public void shouldNotResolveTilde() throws Exception
    {
        mockWindowsXP();

        assertEquals("~", replaceEnvironmentVariables("~"));
    }

    // N.B. this method mocks just enough environment settings that we rely on for the internal
    //   logic of OSUtilWindows to work.
    private void mockWindowsXP() throws Exception
    {
        /*
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
        */
    }
}
