/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.mock.logical;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;

import java.sql.SQLException;

/**
 * Custom matcher to check the path resolution of a SOID
 */
public class IsSOIDAtPath extends ArgumentMatcher<SOID>
{
    private final DirectoryService _ds;
    private final Path _path;

    public IsSOIDAtPath(DirectoryService ds, SID rootSID, String path)
    {
        _ds = ds;
        _path = Path.fromString(rootSID, path);
    }

    @Override
    public boolean matches(Object item)
    {
        try {
            return item.equals(_ds.resolveNullable_(_path));
        } catch (SQLException e) {
            throw new AssertionError();
        }
    }

    @Override
    public void describeTo(Description description)
    {
        description.appendText("<SOID @ ").appendValue(_path).appendText(">");
    }
}
