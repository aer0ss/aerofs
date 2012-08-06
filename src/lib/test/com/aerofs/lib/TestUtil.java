/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.lib.Util.FileName;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestUtil
{
    @Test
    public void shouldSplitFilename()
    {
        FileName result;

        // standard case
        result = Util.splitFileName("abc.def");
        assertEquals(result.name, "abc");
        assertEquals(result.extension, ".def");

        // no extension
        result = Util.splitFileName("abcdef");
        assertEquals(result.name, "abcdef");
        assertEquals(result.extension, "");

        // no name (.file)
        result = Util.splitFileName(".def");
        assertEquals(result.name, ".def");
        assertEquals(result.extension, "");

        // corner case: just a dot
        result = Util.splitFileName(".");
        assertEquals(result.name, ".");
        assertEquals(result.extension, "");

        // corner case: empty name
        result = Util.splitFileName("");
        assertEquals(result.name, "");
        assertEquals(result.extension, "");

        // several dots in name
        result = Util.splitFileName("ab.cd.ef");
        assertEquals(result.name, "ab.cd");
        assertEquals(result.extension, ".ef");

        // counter-intuitive result, but technically correct
        result = Util.splitFileName("..abc");
        assertEquals(result.name, ".");
        assertEquals(result.extension, ".abc");
    }

}
