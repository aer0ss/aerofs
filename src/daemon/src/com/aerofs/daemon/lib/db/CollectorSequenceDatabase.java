package com.aerofs.daemon.lib.db;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.aerofs.daemon.core.collector.CollectorSeq;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.OCID;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.google.inject.Inject;

import javax.annotation.Nullable;

public class CollectorSequenceDatabase extends AbstractDatabase
        implements ICollectorSequenceDatabase
{
    @Inject
    public CollectorSequenceDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private static class DBIterComWithKML extends AbstractDBIterator<OCIDAndCS>
    {
        DBIterComWithKML(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public OCIDAndCS get_() throws SQLException
        {
            CollectorSeq cs = new CollectorSeq(_rs.getLong(1));
            OCID ocid = new OCID(new OID(_rs.getBytes(2)), new CID(_rs.getInt(3)));
            return new OCIDAndCS(ocid, cs);
        }
    }

    private IDBIterator<OCIDAndCS> getFromCSImpl_(SIndex sidx, @Nullable CollectorSeq csStart,
            int limit, PreparedStatementWrapper psw)
            throws SQLException
    {
        try {
            PreparedStatement ps = psw.get(c());
            ps.setInt(1, sidx.getInt());
            ps.setLong(2, csStart == null ? 0 : csStart.getLong());
            ps.setInt(3, limit);
            return new DBIterComWithKML(ps.executeQuery());
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    private static String CSQuery(@Nullable String extraCondition) {
        return DBUtil.selectWhere(T_CS,
                C_CS_SIDX + "=? and " + C_CS_CS + ">?" +
                        (extraCondition == null ? "" : " and " + extraCondition),
                C_CS_CS, C_CS_OID, C_CS_CID
                ) +
                " order by " + C_CS_CS +
                " limit ?";
    }

    private final PreparedStatementWrapper _pswGCS = new PreparedStatementWrapper(CSQuery(null));
    @Override
    public IDBIterator<OCIDAndCS> getCS_(SIndex sidx, @Nullable CollectorSeq csStart, int limit)
        throws SQLException
    {
        // To avoid requiring a temporary b-tree for the "order by CS_CS,"
        // the CS table index was changed to have (sidx, cs, oid, cid)
        return getFromCSImpl_(sidx, csStart, limit, _pswGCS);
    }

    private final PreparedStatementWrapper _pswGMCS = new PreparedStatementWrapper(
            CSQuery(C_CS_CID + "=" + CID.META.getInt()));
    @Override
    public IDBIterator<OCIDAndCS> getMetaCS_(SIndex sidx, @Nullable CollectorSeq csStart, int limit)
        throws SQLException
    {
        return getFromCSImpl_(sidx, csStart, limit, _pswGMCS);
    }

    private PreparedStatement _psDCS;
    @Override
    public void deleteCS_(CollectorSeq cs, Trans t) throws SQLException
    {
        try {
            if (_psDCS == null) _psDCS = c()
                    .prepareStatement("delete from " + T_CS +
                            " where " + C_CS_CS + "=?");

            _psDCS.setLong(1, cs.getLong());
            _psDCS.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psDCS);
            _psDCS = null;
            throw detectCorruption(e);
        }
    }


    private PreparedStatement _psACS;
    @Override
    public void insertCS_(SOCID socid, Trans t) throws SQLException
    {
        try {
            if (_psACS == null) _psACS = c()
                    .prepareStatement("insert " + (_dbcw.isMySQL() ? "" : "or ") +
                            " ignore into " + T_CS + "(" + C_CS_SIDX + "," +
                            C_CS_OID + "," + C_CS_CID + ") values (?,?,?)");

            _psACS.setInt(1, socid.sidx().getInt());
            _psACS.setBytes(2, socid.oid().getBytes());
            _psACS.setInt(3, socid.cid().getInt());
            _psACS.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psACS);
            _psACS = null;
            throw detectCorruption(e);
        }
    }

    @Override
    public void deleteCSsForStore_(SIndex sidx, Trans t)
            throws SQLException
    {
        StoreDatabase.deleteRowsInTableForStore_(T_CS, C_CS_SIDX, sidx, c(), t);
    }
}
