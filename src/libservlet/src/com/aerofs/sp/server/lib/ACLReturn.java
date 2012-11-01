/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib;

import com.aerofs.lib.SubjectRolePair;
import com.aerofs.lib.id.SID;

import java.util.List;
import java.util.Map;

public final class ACLReturn
{
    private final long _epoch;
    private final Map<SID, List<SubjectRolePair>> _sidToPairs;

    ACLReturn(long epoch, Map<SID, List<SubjectRolePair>> sidToPairs)
    {
        this._epoch = epoch;
        this._sidToPairs = sidToPairs;
    }

    public long getEpoch()
    {
        return _epoch;
    }

    public Map<SID, List<SubjectRolePair>> getSidToPairs()
    {
        return _sidToPairs;
    }
}
