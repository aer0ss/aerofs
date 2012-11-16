/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.sumu.multiuser.migration;

import com.aerofs.daemon.core.migration.IEmigrantCreator;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class NullEmigrantCreator implements IEmigrantCreator
{
    @Override
    public List<SID> getEmigrantTargetAncestorSIDsForMeta_(OID oidParent, String name)
            throws SQLException
    {
        return Collections.emptyList();
    }
}
