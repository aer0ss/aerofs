/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.os;

import junit.framework.Assert;
import org.junit.Test;

/**
 *
 */
public class TestOSUtilWindows
{
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
            Assert.assertFalse("Should be illegal: " + s, OSUtilWindows.isValidFileName(s));
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
            Assert.assertTrue("Should be legal: " + s, OSUtilWindows.isValidFileName(s));
        }
    }

}
