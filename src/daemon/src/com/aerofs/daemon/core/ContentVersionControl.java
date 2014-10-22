/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core;

import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.SQLException;

// TODO: move as much distrib/central switcharoo as possible into this class
public class ContentVersionControl
{
    private final ChangeEpochDatabase _cedb;
    private final CentralVersionDatabase _cvdb;
    private final ContentChangesDatabase _ccdb;
    private final NativeVersionControl _nvc;

    @Inject
    public ContentVersionControl(ChangeEpochDatabase cedb, CentralVersionDatabase cvdb,
            ContentChangesDatabase ccdb, NativeVersionControl nvc)
    {
        _cedb = cedb;
        _cvdb = cvdb;
        _ccdb = ccdb;
        _nvc = nvc;
    }

    public void fileExpelled_(SOID soid, Trans t) throws SQLException
    {
        if (_cedb.getChangeEpoch_(soid.sidx()) != null) {
            _cvdb.deleteVersion_(soid.sidx(), soid.oid(), t);
            _ccdb.deleteChange_(soid.sidx(), soid.oid(), t);
        } else {
            _nvc.moveAllContentTicksToKML_(soid, t);
        }
    }
}
