package com.aerofs.daemon.lib.db.ver;

import static com.aerofs.daemon.lib.db.CoreSchema.C_IV_CID;
import static com.aerofs.daemon.lib.db.CoreSchema.C_IV_DID;
import static com.aerofs.daemon.lib.db.CoreSchema.C_IV_IMM_DID;
import static com.aerofs.daemon.lib.db.CoreSchema.C_IV_IMM_TICK;
import static com.aerofs.daemon.lib.db.CoreSchema.C_IV_OID;
import static com.aerofs.daemon.lib.db.CoreSchema.C_IV_SIDX;
import static com.aerofs.daemon.lib.db.CoreSchema.C_IV_TICK;
import static com.aerofs.daemon.lib.db.CoreSchema.T_IV;
import static com.aerofs.daemon.lib.db.CoreSchema.C_GT_IMMIGRANT;
import static com.aerofs.daemon.lib.db.CoreSchema.T_IK;
import static com.aerofs.daemon.lib.db.CoreSchema.C_IK_SIDX;
import static com.aerofs.daemon.lib.db.CoreSchema.C_IK_IMM_DID;
import static com.aerofs.daemon.lib.db.CoreSchema.C_IK_IMM_TICK;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.id.CID;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import javax.annotation.Nonnull;

public class ImmigrantVersionDatabase
        extends AbstractVersionDatabase<ImmigrantTickRow>
        implements IImmigrantVersionDatabase
{
    private final CfgLocalDID _localDID;

    @Inject
    public ImmigrantVersionDatabase(CoreDBCW dbcw, CfgLocalDID  localDID)
    {
        super(dbcw.get(), C_GT_IMMIGRANT, T_IK, C_IK_SIDX, C_IK_IMM_DID, C_IK_IMM_TICK, T_IV,
                C_IV_IMM_DID, C_IV_SIDX, C_IV_TICK, C_IV_CID, C_IV_OID);
        _localDID = localDID;
    }

    private PreparedStatement _psGetImmTicks;
    @Override
    public @Nonnull IDBIterator<ImmigrantTickRow> getMaxTicks_(SIndex sidx, DID immdid,
            Tick from)
        throws SQLException
    {
        try {
            if (_psGetImmTicks == null) {
                _psGetImmTicks = c().prepareStatement(
                    "select " + C_IV_OID + "," + C_IV_CID + "," +
                    C_IV_DID + "," + C_IV_TICK + "," + C_IV_IMM_TICK +
                    " from " + T_IV +
                    " where " + C_IV_SIDX + "=? and " + C_IV_IMM_DID +
                    "=? and " + C_IV_IMM_TICK + "> ? order by " +
                    // Without the line below, mysql would use filesort for the "order by" clause
                    (_dbcw.isMySQL() ? C_IV_SIDX + "," + C_IV_IMM_DID + "," : "") +
                    C_IV_IMM_TICK + " asc");
            }

            _psGetImmTicks.setInt(1, sidx.getInt());
            _psGetImmTicks.setBytes(2, immdid.getBytes());
            _psGetImmTicks.setLong(3, from.getLong());

            return new DBIterImmigrantTickRow(_psGetImmTicks.executeQuery());
        } catch (SQLException e) {
            DBUtil.close(_psGetImmTicks);
            _psGetImmTicks = null;
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswDelRemoteTicks = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_IV, C_IV_SIDX + "=? and " + C_IV_IMM_DID + "!=?"));
    @Override
    public void deleteTicksAndKnowledgeForStore_(SIndex sidx, Trans t) throws SQLException
    {
        StoreDatabase.deleteRowsInTablesForStore_(
                ImmutableMap.of(T_IK, C_IK_SIDX), sidx, c(), t);

        // discard imm ticks for remote devices
        // keep local ticks in case the store is re-admitted in the future
        try {
            PreparedStatement ps = _pswDelRemoteTicks.get(c());
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, _localDID.get().getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswDelRemoteTicks.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psAddIMMVer;
    @Override
    public void addImmigrantVersion_(SOCID socid, DID immDid, Tick immTick, DID did, Tick tick,
            Trans t) throws SQLException
    {
        try {
            if (_psAddIMMVer == null) _psAddIMMVer = c()
                    .prepareStatement("replace into "
                            + T_IV + "(" + C_IV_SIDX + "," + C_IV_OID + ","
                            + C_IV_CID + "," + C_IV_DID + "," + C_IV_TICK + ","
                            + C_IV_IMM_DID + "," + C_IV_IMM_TICK +
                            ") values (?,?,?,?,?,?,?)");

            _psAddIMMVer.setInt(1, socid.sidx().getInt());
            _psAddIMMVer.setBytes(2, socid.oid().getBytes());
            _psAddIMMVer.setInt(3, socid.cid().getInt());
            _psAddIMMVer.setBytes(4, did.getBytes());
            _psAddIMMVer.setLong(5, tick.getLong());
            _psAddIMMVer.setBytes(6, immDid.getBytes());
            _psAddIMMVer.setLong(7, immTick.getLong());
            _psAddIMMVer.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psAddIMMVer);
            _psAddIMMVer = null;
            throw detectCorruption(e);
        }
    }

    private static class DBIterImmigrantTickRow
        extends AbstractDBIterator<ImmigrantTickRow>
    {
        DBIterImmigrantTickRow(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public ImmigrantTickRow get_() throws SQLException
        {
            return new ImmigrantTickRow(
                                        new OID(_rs.getBytes(1)),
                                        new CID(_rs.getInt(2)),
                                        new DID(_rs.getBytes(3)),
                                        new Tick(_rs.getLong(4)),
                                        new Tick(_rs.getLong(5))
                                        );
        }
    }

    @Override
    public @Nonnull IDBIterator<ImmigrantTickRow> getBackupTicks_(SIndex sidx)
            throws SQLException
    {
        return new IDBIterator<ImmigrantTickRow>() {
            @Override
            public ImmigrantTickRow get_() throws SQLException
            {
                return null;
            }

            @Override
            public boolean next_() throws SQLException
            {
                return false;
            }

            @Override
            public void close_() throws SQLException
            {}

            @Override
            public boolean closed_()
            {
                return false;
            }
        };
    }

    // FIXME this is duplicated from deleteMaxTicksForSOCID_ in
    // NativeVersionDatabase. Consolidate?
    private PreparedStatement _psDelImmTick;
    @Override
    public void deleteImmigrantTicks_(SOCID socid, Trans t)
            throws SQLException
    {
        try {
            if (_psDelImmTick == null) _psDelImmTick =
                    c().prepareStatement("delete from " + T_IV + " where " +
                        C_IV_SIDX + "=? and " +
                        C_IV_OID + "=? and " +
                        C_IV_CID + "=?");
            _psDelImmTick.setInt(1, socid.sidx().getInt());
            _psDelImmTick.setBytes(2, socid.oid().getBytes());
            _psDelImmTick.setInt(3, socid.cid().getInt());

            _psDelImmTick.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psDelImmTick);
            _psDelImmTick = null;
            throw detectCorruption(e);
        }
    }
}
