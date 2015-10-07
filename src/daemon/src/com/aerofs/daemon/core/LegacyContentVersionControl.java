package com.aerofs.daemon.core;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.SQLException;

public class LegacyContentVersionControl implements IContentVersionControl {

    private final NativeVersionControl _nvc;

    @Inject
    public LegacyContentVersionControl(NativeVersionControl nvc)
    {
        _nvc = nvc;
    }

    @Override
    public void fileExpelled_(SOID soid, Trans t) throws SQLException
    {
        _nvc.moveAllContentTicksToKML_(soid, t);
    }
}
