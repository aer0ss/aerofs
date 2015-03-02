package com.aerofs.daemon.lib.db;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

public class StoreContributorsDatabase
        extends AbstractDatabase
        implements IStoreContributorsDatabase
{
    @Inject
    public StoreContributorsDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private final PreparedStatementWrapper _pswAddContrib = new PreparedStatementWrapper(
            DBUtil.insert(T_SC, C_SC_SIDX, C_SC_DID));
    @Override
    public void addContributor_(SIndex sidx, DID did, Trans t)
            throws SQLException
    {
        try {
            PreparedStatement ps = _pswAddContrib.get(c());
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, did.getBytes());
            int n = ps.executeUpdate();
            Util.verify(n == 1);
        } catch (SQLException e) {
            _pswAddContrib.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswGetContrib = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_SC, C_SC_SIDX + "=?", C_SC_DID));
    @Override
    public Set<DID> getContributors_(SIndex sidx)
            throws SQLException
    {
        try {
            PreparedStatement ps = _pswGetContrib.get(c());
            ps.setInt(1, sidx.getInt());
            Set<DID> dids = Sets.newHashSet();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dids.add(new DID(rs.getBytes(1)));
                }
                return dids;
            }
        } catch (SQLException e) {
            _pswGetContrib.close();
            throw detectCorruption(e);
        }
    }
}
