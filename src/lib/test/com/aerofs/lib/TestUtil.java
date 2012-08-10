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
        assertEquals(result.base, "abc");
        assertEquals(result.extension, ".def");

        // no extension
        result = Util.splitFileName("abcdef");
        assertEquals(result.base, "abcdef");
        assertEquals(result.extension, "");

        // no name (.file)
        result = Util.splitFileName(".def");
        assertEquals(result.base, ".def");
        assertEquals(result.extension, "");

        // corner case: just a dot
        result = Util.splitFileName(".");
        assertEquals(result.base, ".");
        assertEquals(result.extension, "");

        // corner case: empty name
        result = Util.splitFileName("");
        assertEquals(result.base, "");
        assertEquals(result.extension, "");

        // several dots in name
        result = Util.splitFileName("ab.cd.ef");
        assertEquals(result.base, "ab.cd");
        assertEquals(result.extension, ".ef");

        // counter-intuitive result, but technically correct
        result = Util.splitFileName("..abc");
        assertEquals(result.base, ".");
        assertEquals(result.extension, ".abc");
    }

}
