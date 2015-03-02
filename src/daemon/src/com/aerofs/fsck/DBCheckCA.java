package com.aerofs.fsck;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.google.inject.Inject;
import static com.aerofs.daemon.lib.db.CoreSchema.*;

import com.aerofs.lib.InOutArg;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.KIndex;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;

public class DBCheckCA
{
    private final IDBCW _dbcw;

    @Inject
    public DBCheckCA(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    public void check_(InOutArg<Boolean> okay) throws SQLException
    {
        try (Statement stmt = _dbcw.getConnection().createStatement()) {
            try (ResultSet rs = stmt.executeQuery(DBUtil.selectWhere(T_CA, C_CA_HASH + " is null",
                    C_CA_SIDX, C_CA_OID, C_CA_KIDX))) {
                while (rs.next()) {
                    KIndex kidx = new KIndex(rs.getInt(3));
                    if (!kidx.equals(KIndex.MASTER)) {
                        SIndex sidx = new SIndex(rs.getInt(1));
                        OID oid = new OID(rs.getBytes(2));
                        String offender = sidx + "." + oid.toStringFormal() + "." + kidx;
                        DBChecker.error("non-masters have content hash", offender, okay);
                    }
                }
            }
        }
    }
}
