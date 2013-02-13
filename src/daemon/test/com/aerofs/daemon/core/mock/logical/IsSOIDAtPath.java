/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.mock.logical;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import junit.framework.Assert;
import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;

import java.sql.SQLException;

/**
 * Custom matcher to check the path resolution of a SOID
 */
public class IsSOIDAtPath extends ArgumentMatcher<SOID>
{
    private final DirectoryService _ds;
    private final String _path;

    public IsSOIDAtPath(DirectoryService ds, String path)
    {
        _ds = ds;
        _path = path;
    }

    @Override
    public boolean matches(Object argument)
    {
        Assert.assertNotNull(argument);
        try {
            Path p = _ds.resolve_((SOID) argument);
            Assert.assertNotNull("expected " + argument + " to point to " + _path, p);
            return _path.equalsIgnoreCase(p.toStringFormal());
        } catch (SQLException e) {
            Assert.fail();
            return false;
        }
    }

    @Override
    public void describeTo(Description description)
    {
        description.appendText("<SOID @ ").appendValue(_path).appendText(">");
    }
}
