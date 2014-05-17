/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.base.ex.ExBadArgs;
import com.google.common.base.Strings;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestFullName
{
    @Test
    public void shouldTrimEmptyNames()
    {
        assertEquals(new FullName("Foo", "").getString(), "Foo");
        assertEquals(new FullName("", "Bar").getString(), "Bar");
    }

    @Test
    public void shouldOutputUnknownUserForEmptyFullName()
    {
        assertEquals(new FullName("", "").getString(), "Unknown User");
    }

    @Test
    public void shouldCombine()
    {
        assertEquals(new FullName("A", "B").getString(), "A B");
    }

    @Test
    public void shouldHandleNulls() throws Exception
    {
        shouldFail(null, null, NullPointerException.class);
        shouldFail(null, "last", NullPointerException.class);
        shouldFail("first", null, NullPointerException.class);
    }

    @Test
    public void shouldHandleEmpties() throws Exception
    {
        shouldFail(" ", " ", ExBadArgs.class);
        shouldFail("first", "", ExBadArgs.class);
        shouldFail(" ", "last", ExBadArgs.class);
    }

    @Test
    public void shouldFromExternal() throws Exception
    {
        shouldWork("Firsty", "Lasto");
    }

    private void shouldWork(String first, String last) throws ExBadArgs
    {
        FullName fullName = FullName.fromExternal(first, last);
        Assert.assertTrue(fullName != null);
        Assert.assertFalse(Strings.isNullOrEmpty(fullName._first));
        Assert.assertFalse(Strings.isNullOrEmpty(fullName._last));
    }

    private void shouldFail(String first, String last, Class<?> expectedException) throws ExBadArgs
    {
        try {
            FullName.fromExternal(first, last);
            Assert.fail("should have thrown here");
        } catch (Exception e) {
            Assert.assertEquals(expectedException, e.getClass());
        }
    }
}
