package com.aerofs.daemon.lib.db.ver;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;

import java.sql.SQLException;

public interface IPrefixVersionDatabase
{
    Version getPrefixVersion_(SOID soid, KIndex kidx) throws SQLException;

    void insertPrefixVersion_(SOID soid, KIndex kidx, Version v, Trans t) throws SQLException;

    void deletePrefixVersion_(SOID soid, KIndex kidx, Trans t) throws SQLException;

    void deleteAllPrefixVersions_(SOID soid, Trans t) throws SQLException;
}

