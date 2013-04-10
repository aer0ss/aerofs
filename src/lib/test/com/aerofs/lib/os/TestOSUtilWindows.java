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
                "foo:", "foo\"", "foo/",
                "foo\\", "foo|", "foo?", "foo*",
                "...", "....", "....."
        };

        for (String s : IllegalNames) {
            Assert.assertFalse(OSUtilWindows.isValidFileName(s));
        }
    }
    @Test
    public void shouldAllowValidFiles()
    {
        String[] LegalNames = {
                "_con", ".con",
                "foo,", "lpt0",
                "null", "null.nul", "...foo.foo"
        };
        for (String s : LegalNames)
        {
            Assert.assertTrue(OSUtilWindows.isValidFileName(s));
        }
    }

}
