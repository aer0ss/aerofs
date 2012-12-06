/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestFullName
{
    @Test
    public void shouldTrimEmptyNames()
    {
        assertEquals(new FullName("Foo", "").toString(), "Foo");
        assertEquals(new FullName("", "Bar").toString(), "Bar");
    }

    @Test
    public void shouldOutputUnknownUserForEmptyFullName()
    {
        assertEquals(new FullName("", "").toString(), "Unknown User");
    }

    @Test
    public void shouldCombine()
    {
        assertEquals(new FullName("A", "B").toString(), "A B");
    }
}
