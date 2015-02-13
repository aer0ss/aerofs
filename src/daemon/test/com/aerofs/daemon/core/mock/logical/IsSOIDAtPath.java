/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.mock.logical;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
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
    private final boolean _followAnchor;

    public IsSOIDAtPath(DirectoryService ds, SID rootSID, String path, boolean followAnchor)
    {
        _ds = ds;
        _path = Path.fromString(rootSID, path);
        _followAnchor = followAnchor;
    }

    @Override
    public boolean matches(Object item)
    {
        try {
            SOID soid = _ds.resolveNullable_(_path);
            OA oa = _ds.getOA_(soid);
            if (_followAnchor && oa.isAnchor()) soid = _ds.followAnchorNullable_(oa);
            return item.equals(soid);
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
