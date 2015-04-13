package com.aerofs.daemon.core.protocol;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.ver.IPrefixVersionDatabase;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.SQLException;

/**
 * Prefix versions are used to detect changes on partially downloaded files.
 * Use this class to fetch/manipulate prefix versions.
 */
// TODO: track prefixes by content hash instead of (soid, version)
public class PrefixVersionControl
{
    private final IPrefixVersionDatabase _pvdb;

    @Inject
    public PrefixVersionControl(IPrefixVersionDatabase pvdb)
    {
        _pvdb = pvdb;
    }

    public Version getPrefixVersion_(SOID soid, KIndex kidx) throws SQLException
    {
        return _pvdb.getPrefixVersion_(soid, kidx);
    }

    public void addPrefixVersion_(SOID soid, KIndex kidx, Version v, Trans t) throws SQLException
    {
        _pvdb.insertPrefixVersion_(soid, kidx, v, t);
    }

    public void deletePrefixVersion_(SOID soid, KIndex kidx, Trans t) throws SQLException
    {
        _pvdb.deletePrefixVersion_(soid, kidx, t);
    }

    public void deleteAllPrefixVersions_(SOID soid, Trans t) throws SQLException
    {
        _pvdb.deleteAllPrefixVersions_(soid, t);
    }
}

