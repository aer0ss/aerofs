package com.aerofs.daemon.core.update;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map.Entry;

import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.*;
import com.google.inject.Inject;

import javax.annotation.Nonnull;

public class NativeVersionDatabase extends AbstractDatabase {

    private final static  String
            // Distributed Versions
            T_VER           = "v",
            C_VER_SIDX      = "v_i",        // SIndex
            C_VER_OID       = "v_o",        // OID
            C_VER_CID       = "v_c",        // CID
            // KIndex, INVALID if not local (null doesn't work well with "replace" statement when
            // adding a KML version)
            C_VER_KIDX      = "v_k",
            C_VER_DID       = "v_d",        // DID
            C_VER_TICK      = "v_t"        // Tick
    ;

    @Inject
    public NativeVersionDatabase(IDBCW dbcw) {
        super(dbcw);
    }

    private PreparedStatement _psDelVer;
    public void deleteVersion_(SIndex sidx, OID oid, CID cid, KIndex kidx, Version v, Trans t)
            throws SQLException
    {
        try {
            if (_psDelVer == null) _psDelVer = c()
                    .prepareStatement("delete from " + T_VER + " where "
                            + C_VER_SIDX + "=? and " + C_VER_OID + "=? and "
                            + C_VER_CID + "=? and " + C_VER_DID + "=? and "
                            + C_VER_KIDX + "=?");

            _psDelVer.setInt(1, sidx.getInt());
            _psDelVer.setBytes(2, oid.getBytes());
            _psDelVer.setInt(3, cid.getInt());
            _psDelVer.setInt(5, kidx.getInt());

            for (Entry<DID, Tick> en : v.getAll_().entrySet()) {
                _psDelVer.setBytes(4, en.getKey().getBytes());
                _psDelVer.addBatch();
            }
            _psDelVer.executeBatch();
        } catch (SQLException e) {
            DBUtil.close(_psDelVer);
            _psDelVer = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGetV;
    @Nonnull
    public Version getVersion_(SIndex sidx, OID oid, CID cid, KIndex kidx) throws SQLException
    {
        try {
            if (_psGetV == null) {
                _psGetV = c().prepareStatement("select " + C_VER_DID + ","
                        + C_VER_TICK + " from " + T_VER + " where "
                        + C_VER_SIDX + "=? and " + C_VER_OID + "=? and "
                        + C_VER_CID + "=? and " + C_VER_KIDX + "=?");
            }

            _psGetV.setInt(1, sidx.getInt());
            _psGetV.setBytes(2, oid.getBytes());
            _psGetV.setInt(3, cid.getInt());
            _psGetV.setInt(4, kidx.getInt());
            try (ResultSet rs = _psGetV.executeQuery()) {
                Version v = Version.empty();
                while (rs.next()) {
                    DID did = new DID(rs.getBytes(1));
                    assert v.get_(did).equals(Tick.ZERO);
                    v.set_(did, rs.getLong(2));
                }
                return v;
            }

        } catch (SQLException e) {
            DBUtil.close(_psGetV);
            _psGetV = null;
            throw detectCorruption(e);
        }
    }
}
