/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.lib.FileUtil.FileName;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestUtil
{
    @Test
    public void shouldSplitFilename()
    {
        FileName result;

        // standard case
        result = FileName.fromBaseName("abc.def");
        assertEquals(result.base, "abc");
        assertEquals(result.extension, ".def");

        // no extension
        result = FileName.fromBaseName("abcdef");
        assertEquals(result.base, "abcdef");
        assertEquals(result.extension, "");

        // no name (.file)
        result = FileName.fromBaseName(".def");
        assertEquals(result.base, ".def");
        assertEquals(result.extension, "");

        // corner case: just a dot
        result = FileName.fromBaseName(".");
        assertEquals(result.base, ".");
        assertEquals(result.extension, "");

        // corner case: empty name
        result = FileName.fromBaseName("");
        assertEquals(result.base, "");
        assertEquals(result.extension, "");

        // several dots in name
        result = FileName.fromBaseName("ab.cd.ef");
        assertEquals(result.base, "ab.cd");
        assertEquals(result.extension, ".ef");

        // counter-intuitive result, but technically correct
        result = FileName.fromBaseName("..abc");
        assertEquals(result.base, ".");
        assertEquals(result.extension, ".abc");
    }

}
