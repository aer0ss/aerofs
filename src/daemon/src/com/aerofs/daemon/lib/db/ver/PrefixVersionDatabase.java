package com.aerofs.daemon.lib.db.ver;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map.Entry;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

public class PrefixVersionDatabase extends AbstractDatabase implements IPrefixVersionDatabase
{
    @Inject
    public PrefixVersionDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private PreparedStatement _psGetPV;
    @Override
    public Version getPrefixVersion_(SOID soid, KIndex kidx) throws SQLException
    {
        try {
            if (_psGetPV == null) {
                _psGetPV = c().prepareStatement("select " + C_PRE_DID + ","
                        + C_PRE_TICK + " from " + T_PRE + " where "
                        + C_PRE_SIDX + "=? and " + C_PRE_OID + "=? and "
                        + C_PRE_KIDX + "=?");
            }

            _psGetPV.setInt(1, soid.sidx().getInt());
            _psGetPV.setBytes(2, soid.oid().getBytes());
            _psGetPV.setInt(3, kidx.getInt());
            ResultSet rs = _psGetPV.executeQuery();
            try {
                Version v = Version.empty();
                while (rs.next()) {
                    DID did = new DID(rs.getBytes(1));
                    assert v.get_(did).equals(Tick.ZERO);
                    v.set_(did, rs.getLong(2));
                }
                return v;
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            DBUtil.close(_psGetPV);
            _psGetPV = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psAddPV;
    @Override
    public void insertPrefixVersion_(SOID soid, KIndex kidx, Version v, Trans t)
            throws SQLException
    {
        try {
            if (_psAddPV == null) _psAddPV = c().prepareStatement("insert into "
                    + T_PRE + "(" + C_PRE_SIDX + "," + C_PRE_OID + ","
                    + C_PRE_KIDX + "," + C_PRE_DID + ","
                    + C_PRE_TICK + ") values (?,?,?,?,?)");

            _psAddPV.setInt(1, soid.sidx().getInt());
            _psAddPV.setBytes(2, soid.oid().getBytes());
            _psAddPV.setInt(3, kidx.getInt());

            for (Entry<DID, Tick> en : v.getAll_().entrySet()) {
                assert en.getValue().getLong() != 0;
                _psAddPV.setBytes(4, en.getKey().getBytes());
                _psAddPV.setLong(5, en.getValue().getLong());
                _psAddPV.addBatch();
            }

            _psAddPV.executeBatch();

        } catch (SQLException e) {
            DBUtil.close(_psAddPV);
            _psAddPV = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDelPV;
    @Override
    public void deletePrefixVersion_(SOID soid, KIndex kidx, Trans t) throws SQLException
    {
        try {
            if (_psDelPV == null) _psDelPV = c()
                    .prepareStatement("delete from " + T_PRE + " where "
                            + C_PRE_SIDX + "=? and " + C_PRE_OID + "=? and "
                            + C_PRE_KIDX + "=?");

            _psDelPV.setInt(1, soid.sidx().getInt());
            _psDelPV.setBytes(2, soid.oid().getBytes());
            _psDelPV.setInt(3, kidx.getInt());

            _psDelPV.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psDelPV);
            _psDelPV = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDelAPV;
    @Override
    public void deleteAllPrefixVersions_(SOID soid, Trans t) throws SQLException
    {
        try {
            if (_psDelAPV == null) _psDelAPV = c()
                    .prepareStatement(DBUtil.deleteWhere(T_PRE,
                            C_PRE_SIDX + "=? and " + C_PRE_OID + "=?"));

            _psDelAPV.setInt(1, soid.sidx().getInt());
            _psDelAPV.setBytes(2, soid.oid().getBytes());

            _psDelAPV.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psDelAPV);
            _psDelAPV = null;
            throw detectCorruption(e);
        }
    }
}

