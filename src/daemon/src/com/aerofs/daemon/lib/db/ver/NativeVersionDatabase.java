package com.aerofs.daemon.lib.db.ver;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map.Entry;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NativeVersionDatabase
        extends AbstractVersionDatabase<NativeTickRow>
        implements INativeVersionDatabase
{
    private final CfgLocalDID _cfgLocalDID;

    @Inject
    public NativeVersionDatabase(CoreDBCW dbcw, CfgLocalDID cfgLocalDID)
    {
        super(dbcw.get(), C_GT_NATIVE, T_KWLG, C_KWLG_SIDX, C_KWLG_DID, C_KWLG_TICK, T_VER, C_VER_DID,
                C_VER_SIDX, T_BKUPT, C_BKUPT_SIDX, C_VER_TICK, C_VER_CID, C_VER_OID);
        _cfgLocalDID = cfgLocalDID;
    }

    @Override
    protected PreparedStatement createInsertBackupStatement() throws SQLException
    {
        return c().prepareStatement("insert into "
                    + T_BKUPT + "(" + C_BKUPT_SIDX + "," + C_BKUPT_OID + ","
                    + C_BKUPT_CID + "," + C_BKUPT_TICK
                    + ") values (?,?,?,?)");
    }

    @Override
    protected void setAddBackupParameters(PreparedStatement ps, SIndex sidx, NativeTickRow tr)
            throws SQLException
    {
        assert tr._tick.getLong() != 0;
        ps.setInt(1, sidx.getInt());
        ps.setBytes(2, tr._oid.getBytes());
        ps.setInt(3, tr._cid.getInt());
        ps.setLong(4, tr._tick.getLong());
    }

    private PreparedStatement _psGetTicks;
    @Override
    public @Nonnull IDBIterator<NativeTickRow> getMaxTicks_(SIndex sidx, DID did, Tick from)
        throws SQLException
    {
        try {

            if (_psGetTicks == null) {
                _psGetTicks = c().prepareStatement(
                    "select " + C_MAXTICK_OID + "," + C_MAXTICK_CID + "," +
                    C_MAXTICK_MAX_TICK + " from " + T_MAXTICK +
                    " where " + C_MAXTICK_SIDX + "=? and " + C_MAXTICK_DID +
                    "=? and " + C_MAXTICK_MAX_TICK + "> ? order by " +
                    // Without the line below, mysql would use filesort for the "order by" clause
                    (_dbcw.isMySQL() ? C_MAXTICK_SIDX + "," + C_MAXTICK_DID + "," : "") +
                    C_MAXTICK_MAX_TICK + " asc");
            }

            _psGetTicks.setInt(1, sidx.getInt());
            _psGetTicks.setBytes(2, did.getBytes());
            _psGetTicks.setLong(3, from.getLong());

            return new DBIterNativeTickRow(_psGetTicks.executeQuery());
        } catch (SQLException e) {
            DBUtil.close(_psGetTicks);
            _psGetTicks = null;

            throw e;
        }
    }

    @Override
    public void deleteTicksAndKnowledgeForStore_(SIndex sidx, Trans t) throws SQLException
    {
        StoreDatabase.deleteRowsInTablesForStore_(
                ImmutableMap.of(T_VER, C_VER_SIDX, T_MAXTICK, C_MAXTICK_SIDX, T_KWLG, C_KWLG_SIDX),
                sidx, c(), t);
    }

    @Override
    public void addLocalVersion_(SOCKID k, Version v, Trans t)
            throws SQLException
    {
        addVersion_(k.socid(), k.kidx(), v, t);
    }

    @Override
    public void addKMLVersion_(SOCID socid, Version v, Trans t)
            throws SQLException
    {
        addVersion_(socid, KIndex.KML, v, t);
    }


    private PreparedStatement _psAddV;
    private void addVersion_(SOCID socid, KIndex kidx, Version v, Trans t)
        throws SQLException
    {
        try {
            if (_psAddV == null) _psAddV = c().prepareStatement("replace into "
                    + T_VER + "(" + C_VER_SIDX + "," + C_VER_OID + ","
                    + C_VER_CID + "," + C_VER_DID + "," + C_VER_TICK + ","
                    + C_VER_KIDX + ") values (?,?,?,?,?,?)");

            _psAddV.setInt(1, socid.sidx().getInt());
            _psAddV.setBytes(2, socid.oid().getBytes());
            _psAddV.setInt(3, socid.cid().getInt());
            _psAddV.setInt(6, kidx.getInt());

            for (Entry<DID, Tick> en : v.getAll_().entrySet()) {
                assert en.getValue().getLong() != 0;
                _psAddV.setBytes(4, en.getKey().getBytes());
                _psAddV.setLong(5, en.getValue().getLong());
                _psAddV.addBatch();
            }

            _psAddV.executeBatch();

        } catch (SQLException e) {
            DBUtil.close(_psAddV);
            _psAddV = null;
            throw e;
        }
    }

    @Override
    public void deleteLocalVersion_(SOCKID k, Version v, Trans t)
            throws SQLException
    {
        deleteVersion_(k.socid(), k.kidx(), v, t);
    }

    @Override
    public void deleteKMLVersion_(SOCID socid, Version v, Trans t)
            throws SQLException
    {
        deleteVersion_(socid, KIndex.KML, v, t);
    }


    private PreparedStatement _psDelVer;
    private void deleteVersion_(SOCID socid, KIndex kidx, Version v, Trans t)
            throws SQLException
    {
        try {
            if (_psDelVer == null) _psDelVer = c()
                    .prepareStatement("delete from " + T_VER + " where "
                            + C_VER_SIDX + "=? and " + C_VER_OID + "=? and "
                            + C_VER_CID + "=? and " + C_VER_DID + "=? and "
                            + C_VER_KIDX + "=?");

            _psDelVer.setInt(1, socid.sidx().getInt());
            _psDelVer.setBytes(2, socid.oid().getBytes());
            _psDelVer.setInt(3, socid.cid().getInt());
            _psDelVer.setInt(5, kidx.getInt());

            for (Entry<DID, Tick> en : v.getAll_().entrySet()) {
                _psDelVer.setBytes(4, en.getKey().getBytes());

                _psDelVer.addBatch();
            }

            int[] rets = _psDelVer.executeBatch();

            // sanity check
            assert rets.length == v.getAll_().size();
            for (int ret : rets) {
                assert ret == 1 : Arrays.toString(rets) + " " + v + " " + socid + " " + kidx;
            }

        } catch (SQLException e) {
            DBUtil.close(_psDelVer);
            _psDelVer = null;
            throw e;
        }
    }

    private PreparedStatement _psMoveVer;
    @Override
    public void moveAllLocalVersions_(SOCID alias, SOCID target, Trans t) throws SQLException
    {
        try {
            if (_psMoveVer == null) _psMoveVer = c().prepareStatement(DBUtil.updateWhere(T_VER,
                    C_VER_SIDX + "=? and " + C_VER_OID + "=? and " + C_VER_CID + "=? and "
                            + C_VER_KIDX + "<>" + KIndex.KML.getInt(),
                    C_VER_OID));
            _psMoveVer.setBytes(1, target.oid().getBytes());
            _psMoveVer.setInt(2, alias.sidx().getInt());
            _psMoveVer.setBytes(3, alias.oid().getBytes());
            _psMoveVer.setInt(4, alias.cid().getInt());

            _psMoveVer.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psMoveVer);
            _psMoveVer = null;
            throw e;
        }
    }

    private PreparedStatement _psGLT;
    @Override
    public @Nullable Tick getLocalTick_(SOCKID k) throws SQLException
    {
        try {
            if (_psGLT == null) _psGLT = c()
                    .prepareStatement("select " + C_VER_TICK +
                            " from " + T_VER + " where "
                            + C_VER_SIDX + "=? and " + C_VER_OID + "=? and "
                            + C_VER_CID + "=? and " + C_VER_KIDX + "=? and "
                            + C_VER_DID + "=?");

            _psGLT.setInt(1, k.sidx().getInt());
            _psGLT.setBytes(2, k.oid().getBytes());
            _psGLT.setInt(3, k.cid().getInt());
            _psGLT.setInt(4, k.kidx().getInt());
            _psGLT.setBytes(5, _cfgLocalDID.get().getBytes());
            ResultSet rs = _psGLT.executeQuery();
            try {
                // Util.verify(rs.next());
                // return new Tick(rs.getLong(1));
                return rs.next() ? new Tick(rs.getLong(1)) : null;
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            DBUtil.close(_psGLT);
            _psGLT = null;
            throw e;
        }
    }

    @Override
    public @Nonnull Version getKMLVersion_(SOCID socid) throws SQLException
    {
        return getVersion_(socid, KIndex.KML);
    }

    @Override
    public @Nonnull Version getLocalVersion_(SOCKID k) throws SQLException
    {
        return getVersion_(k.socid(), k.kidx());
    }

    private PreparedStatement _psGetV;
    private @Nonnull Version getVersion_(SOCID socid, KIndex kidx) throws SQLException
    {
        try {
            if (_psGetV == null) {
                _psGetV = c().prepareStatement("select " + C_VER_DID + ","
                        + C_VER_TICK + " from " + T_VER + " where "
                        + C_VER_SIDX + "=? and " + C_VER_OID + "=? and "
                        + C_VER_CID + "=? and " + C_VER_KIDX + "=?");
            }

            _psGetV.setInt(1, socid.sidx().getInt());
            _psGetV.setBytes(2, socid.oid().getBytes());
            _psGetV.setInt(3, socid.cid().getInt());
            _psGetV.setInt(4, kidx.getInt());
            ResultSet rs = _psGetV.executeQuery();
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
            DBUtil.close(_psGetV);
            _psGetV = null;
            throw e;
        }
    }

    private PreparedStatement _psGetALV;
    @Override
    public @Nonnull Version getAllLocalVersions_(SOCID socid) throws SQLException
    {
        try {
            if (_psGetALV == null) {
                _psGetALV = c().prepareStatement(DBUtil.selectWhere(T_VER,
                        C_VER_SIDX + "=? and " + C_VER_OID + "=? and "
                                + C_VER_CID + "=? and " + C_VER_KIDX + "<>" + KIndex.KML.getInt(),
                        C_VER_DID, "max(" + C_VER_TICK + ")")
                        + " group by " + C_VER_DID);
            }

            _psGetALV.setInt(1, socid.sidx().getInt());
            _psGetALV.setBytes(2, socid.oid().getBytes());
            _psGetALV.setInt(3, socid.cid().getInt());
            ResultSet rs = _psGetALV.executeQuery();
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
            DBUtil.close(_psGetALV);
            _psGetALV = null;
            throw e;
        }
    }

    @Override
    public @Nonnull Version getAllVersions_(SOCID socid) throws SQLException
    {
        try {
            // Theoretically we could query the maxticks table for better performance. But because
            // that table is usually updated at the end of transactions, its results may be
            // inaccurate during a transaction.
            if (_psGetMaxTick == null) _psGetMaxTick = prepareGetMaxTicksFromVersionTable();

            _psGetMaxTick.setInt(1, socid.sidx().getInt());
            _psGetMaxTick.setBytes(2, socid.oid().getBytes());
            _psGetMaxTick.setInt(3, socid.cid().getInt());

            ResultSet rs = _psGetMaxTick.executeQuery();
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
            DBUtil.close(_psGetMaxTick);
            _psGetMaxTick = null;
            throw e;
        }
    }

    private PreparedStatement prepareGetMaxTicksFromVersionTable() throws SQLException
    {
        return c().prepareStatement("select " + C_VER_DID +
                ", max(" + C_VER_TICK + ") from " +
                T_VER + " where " + C_VER_SIDX + "=? and " +
                C_VER_OID + "=? and " + C_VER_CID + "=? " +
                "group by " + C_VER_DID);
    }

    private PreparedStatement _psGetMaxTick;
    private PreparedStatement _psAddMaxTick;
    @Override
    public void updateMaxTicks_(SOCID socid, Trans t) throws SQLException
    {
        try {
            // TODO use a single compound SQL statement
            if (_psGetMaxTick == null) _psGetMaxTick = prepareGetMaxTicksFromVersionTable();

            if (_psAddMaxTick == null) _psAddMaxTick =
                    c().prepareStatement("replace into " + T_MAXTICK +
                        "(" + C_MAXTICK_SIDX + ", " + C_MAXTICK_OID + ", " +
                        C_MAXTICK_CID + ", " + C_MAXTICK_DID + ", " +
                        C_MAXTICK_MAX_TICK + ")" + "values (?, ?, ?, ?, ?)");

            int sidx = socid.sidx().getInt();
            byte[] oid = socid.oid().getBytes();
            int cid = socid.cid().getInt();

            _psGetMaxTick.setInt(1, sidx);
            _psGetMaxTick.setBytes(2, oid);
            _psGetMaxTick.setInt(3, cid);

            _psAddMaxTick.setInt(1, sidx);
            _psAddMaxTick.setBytes(2, oid);
            _psAddMaxTick.setInt(3, cid);

            ResultSet rs = _psGetMaxTick.executeQuery();
            try {
                while (rs.next()) {
                    DID did = new DID(rs.getBytes(1));
                    Tick tick = new Tick(rs.getLong(2));
                    _psAddMaxTick.setBytes(4, did.getBytes());
                    _psAddMaxTick.setLong(5, tick.getLong());
                    _psAddMaxTick.addBatch();
                }
            } finally {
                rs.close();
            }
            _psAddMaxTick.executeBatch();
        } catch (SQLException e) {
            // terminate the resources for the first ps
            DBUtil.close(_psGetMaxTick);
            _psGetMaxTick = null;

            // terminate the resources for the second ps
            DBUtil.close(_psAddMaxTick);
            _psAddMaxTick = null;
            throw e;
        }
    }

    private PreparedStatement _psDelMaxTick;
    @Override
    public void deleteMaxTicks_(SOCID socid, Trans t) throws SQLException
    {
        try {
            if (_psDelMaxTick == null) _psDelMaxTick =
                    c().prepareStatement("delete from " + T_MAXTICK + " where " +
                        C_MAXTICK_SIDX + "=? and " +
                        C_MAXTICK_OID + "=? and " +
                        C_MAXTICK_CID + "=?");
            _psDelMaxTick.setInt(1, socid.sidx().getInt());
            _psDelMaxTick.setBytes(2, socid.oid().getBytes());
            _psDelMaxTick.setInt(3, socid.cid().getInt());

            _psDelMaxTick.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psDelMaxTick);
            _psDelMaxTick = null;
            throw e;
        }

    }

    private PreparedStatement _psGetBackupTicks;
    @Override
    public @Nonnull IDBIterator<NativeTickRow> getBackupTicks_(SIndex sidx) throws SQLException
    {
        try {
            if (_psGetBackupTicks == null) {
                _psGetBackupTicks = c().prepareStatement(
                    "select " + C_BKUPT_OID + "," + C_BKUPT_CID + "," +
                    C_BKUPT_TICK + " from " + T_BKUPT +
                    " where " + C_BKUPT_SIDX +
                    "=? order by " +
                    C_BKUPT_TICK + " asc");
            }

            _psGetBackupTicks.setInt(1, sidx.getInt());

            return new DBIterNativeTickRow(_psGetBackupTicks.executeQuery());
        } catch (SQLException e) {
            DBUtil.close(_psGetBackupTicks);
            _psGetBackupTicks = null;
            throw e;
        }
    }

    private static class DBIterNativeTickRow extends AbstractDBIterator<NativeTickRow>
    {
        private DBIterNativeTickRow(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public NativeTickRow get_() throws SQLException
        {
            NativeTickRow tr = new NativeTickRow(
                    new OID(_rs.getBytes(1)),
                    new CID(_rs.getInt(2)),
                    new Tick(_rs.getLong(3))
            );
            return tr;
        }
    }
}
