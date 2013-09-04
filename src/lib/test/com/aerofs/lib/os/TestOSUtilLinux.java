/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.os;

import com.aerofs.testlib.AbstractTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestOSUtilLinux extends AbstractTest
{
    @Test
    public void shouldParseCorrectDistroName()
    {
        final String DEFAULT_NAME = "Linux";

        assertEquals("foo", OSUtilLinux.parseDistroName("xxxPRETTY_NAME=\"foo\"xxx"));
        assertEquals("foo", OSUtilLinux.parseDistroName("xxxPRETTY_NAME=foo\nxxx\""));
        assertEquals("bar", OSUtilLinux.parseDistroName("\nLSB_VERSION=foo\n\nbar"));
        assertEquals("Test (foo)", OSUtilLinux.parseDistroName("Test (foo) () "));
        assertEquals("bar", OSUtilLinux.parseDistroName(" # distro name:\n bar "));
        assertEquals(DEFAULT_NAME, OSUtilLinux.parseDistroName(""));
        assertEquals(DEFAULT_NAME, OSUtilLinux.parseDistroName("12345"));
    }
}
