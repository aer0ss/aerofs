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
        assertEquals(new FullName("Foo", "").combine(), "Foo");
        assertEquals(new FullName("", "Bar").combine(), "Bar");
    }

    @Test
    public void shouldOutputUnknownUserForEmptyFullName()
    {
        assertEquals(new FullName("", "").combine(), "Unknown User");
    }

    @Test
    public void shouldCombine()
    {
        assertEquals(new FullName("A", "B").combine(), "A B");
    }
}
