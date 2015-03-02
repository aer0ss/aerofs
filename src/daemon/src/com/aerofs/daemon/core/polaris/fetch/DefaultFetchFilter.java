package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.SQLException;

public class DefaultFetchFilter extends ContentFetcherIterator.Filter
{
    private final DirectoryService _ds;

    @Inject
    public DefaultFetchFilter(DirectoryService ds)
    {
        _ds = ds;
    }

    @Override
    public Action filter_(SOID soid) throws SQLException
    {
        OA oa = _ds.getOANullable_(soid);
        if (oa == null) return Action.Ignore;
        return oa.isExpelled() ? Action.Remove : Action.Fetch;
    }
}
