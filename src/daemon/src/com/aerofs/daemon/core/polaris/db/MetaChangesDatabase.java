/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

/**
 *
 */
public class MetaChangesDatabase extends AbstractDatabase
{
    @Inject
    public MetaChangesDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    public void insertChange_(SIndex sidx, OID oid, OID parent, String targetName, Trans t)
    {}

    public void deleteChanges_(SIndex sidx, OID oidChild, Trans t)
    {}

    public boolean hasChanges_(SIndex sidx)
    {
        return false;
    }
}
