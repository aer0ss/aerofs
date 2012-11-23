/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.organization;

import com.aerofs.lib.id.IntegerID;

/**
 * Organization ID
 */
public class OrgID extends IntegerID
{
    // The default organization value must be identical to the default id specified in sp.sql.
    static public OrgID DEFAULT = new OrgID(0);

    public OrgID(int i)
    {
        super(i);
    }
}
