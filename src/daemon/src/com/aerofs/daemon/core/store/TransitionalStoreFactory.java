package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.SQLException;


public class TransitionalStoreFactory implements Store.Factory
{
    private final ChangeEpochDatabase _cedb;
    private final LegacyStore.Factory _legacy;
    private final PolarisStore.Factory _polaris;

    @Inject
    public TransitionalStoreFactory(LegacyStore.Factory legacy, PolarisStore.Factory polaris,
                                    ChangeEpochDatabase cedb)
    {
        _cedb = cedb;
        _legacy = legacy;
        _polaris = polaris;
    }

    @Override
    public Store create_(SIndex sidx) throws SQLException {
        return _cedb.getChangeEpoch_(sidx) != null ? _polaris.create_(sidx) : _legacy.create_(sidx);
    }
}
