package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

/**
 * When possible, use the MapAlias2Target class which is a high-level wrapper around this low-level
 * database class.
 */
public class AliasDatabase extends AbstractDatabase implements IAliasDatabase
{
    @Inject
    public AliasDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private PreparedStatement _psSetAliasSOID;
    @Override
    public void addAliasToTargetMapping_(SIndex sidx, OID src, OID target, Trans t)
        throws SQLException
    {
        try {
            if (_psSetAliasSOID == null) _psSetAliasSOID = c()
                .prepareStatement("replace into " + T_ALIAS + " ( " +
                    C_ALIAS_SIDX + "," + C_ALIAS_SOURCE_OID + "," +
                    C_ALIAS_TARGET_OID + ") values (?,?,?)");

            _psSetAliasSOID.setInt(1, sidx.getInt());
            _psSetAliasSOID.setBytes(2, src.getBytes());
            _psSetAliasSOID.setBytes(3, target.getBytes());

            _psSetAliasSOID.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psSetAliasSOID);
            _psSetAliasSOID = null;
            throw e;
        }
    }

    private PreparedStatement _psTargetOID;
    @Override
    public OID getTargetOID_(SIndex sidx, OID src)
        throws SQLException
    {
        try {
            if (_psTargetOID == null) _psTargetOID = c()
                .prepareStatement("select " + C_ALIAS_TARGET_OID + " from " +
                    T_ALIAS + " where " +
                    C_ALIAS_SIDX + "=? and " +
                    C_ALIAS_SOURCE_OID + "=?");
            _psTargetOID.setInt(1, sidx.getInt());
            _psTargetOID.setBytes(2, src.getBytes());
            ResultSet rs = _psTargetOID.executeQuery();
            try {
                return rs.next() ? new OID(rs.getBytes(1)) : null;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psTargetOID);
            _psTargetOID = null;
            throw e;
        }
    }

    PreparedStatement _psResolveAliasChaining;
    @Override
    public void resolveAliasChaining_(SIndex sidx, OID alias, OID target, Trans t)
        throws SQLException
    {
        try {
            if (_psResolveAliasChaining == null) _psResolveAliasChaining = c().
                prepareStatement("update " + T_ALIAS + " set " +
                    C_ALIAS_TARGET_OID + "=? where " +
                    C_ALIAS_SIDX + "=? and " +
                    C_ALIAS_TARGET_OID + "=?");
            _psResolveAliasChaining.setBytes(1, target.getBytes());

            _psResolveAliasChaining.setInt(2, sidx.getInt());
            _psResolveAliasChaining.setBytes(3, alias.getBytes());

            _psResolveAliasChaining.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psResolveAliasChaining);
            _psResolveAliasChaining = null;
            throw e;
        }
    }
}
