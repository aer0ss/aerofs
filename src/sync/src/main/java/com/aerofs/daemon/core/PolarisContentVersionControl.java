/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core;

import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.SQLException;

// TODO: move as much distrib/central switcharoo as possible into this class
public class PolarisContentVersionControl implements IContentVersionControl
{
    private final CentralVersionDatabase _cvdb;
    private final ContentChangesDatabase _ccdb;
    private final ContentFetchQueueDatabase _cfqdb;

    @Inject
    public PolarisContentVersionControl(CentralVersionDatabase cvdb,
            ContentChangesDatabase ccdb, ContentFetchQueueDatabase cfqdb)
    {
        _cvdb = cvdb;
        _ccdb = ccdb;
        _cfqdb = cfqdb;
    }

    @Override
    public void fileExpelled_(SOID soid, Trans t) throws SQLException
    {
        _cvdb.deleteVersion_(soid.sidx(), soid.oid(), t);
        _ccdb.deleteChange_(soid.sidx(), soid.oid(), t);
        _cfqdb.remove_(soid.sidx(), soid.oid(), t);
    }
}
