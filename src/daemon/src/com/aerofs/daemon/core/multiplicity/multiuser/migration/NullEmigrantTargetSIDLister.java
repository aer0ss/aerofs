/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser.migration;

import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class NullEmigrantTargetSIDLister implements IEmigrantTargetSIDLister
{
    @Override
    public List<SID> getEmigrantTargetAncestorSIDsForMeta_(OID oidParent, String name)
            throws SQLException
    {
        return Collections.emptyList();
    }
}
