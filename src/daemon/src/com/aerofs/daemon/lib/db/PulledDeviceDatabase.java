package com.aerofs.daemon.lib.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import static com.aerofs.lib.db.CoreSchema.T_PD;
import static com.aerofs.lib.db.CoreSchema.C_PD_SIDX;
import static com.aerofs.lib.db.CoreSchema.C_PD_DID;

public class PulledDeviceDatabase extends AbstractDatabase implements IPulledDeviceDatabase
{
    @Inject
    public PulledDeviceDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private PreparedStatement _psPDContains;
    @Override
    public boolean contains_(SIndex sidx, DID did) throws SQLException
    {
        try {
            if (_psPDContains == null) {
                _psPDContains = c().prepareStatement("select count(*) from "
                                  + T_PD + " where "
                                  + C_PD_SIDX + "=? and "
                                  + C_PD_DID + "=?");
            }
            _psPDContains.setInt(1, sidx.getInt());
            _psPDContains.setBytes(2, did.getBytes());
            _psPDContains.executeQuery();

            ResultSet rs = _psPDContains.executeQuery();
            try {
                Util.verify(rs.next());
                int resultRows = rs.getInt(1);
                assert !rs.next();
                return (resultRows == 1);
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psPDContains);
            _psPDContains = null;
            throw e;
        }
    }

    private PreparedStatement _psAddToPD;
    @Override
    public void add_(SIndex sidx, DID did, Trans t) throws SQLException
    {
        try {
            if (_psAddToPD == null) {
                _psAddToPD = c().prepareStatement("replace into " + T_PD + "("
                              + C_PD_SIDX + "," + C_PD_DID + ") values (?,?)");
            }

            _psAddToPD.setInt(1, sidx.getInt());
            _psAddToPD.setBytes(2, did.getBytes());
            _psAddToPD.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psAddToPD);
            _psAddToPD = null;
            throw e;
        }
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t) throws SQLException
    {
        Statement stmt = c().createStatement();
        try {
            stmt.executeUpdate("delete from " + T_PD + " where " + C_PD_SIDX +
                    "=" + sidx.getInt());
        } finally {
            DBUtil.close(stmt);
        }
    }
}
