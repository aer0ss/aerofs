package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.ids.OID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
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
    public AliasDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    private PreparedStatement _psSetAliasSOID;
    @Override
    public void insertAliasToTargetMapping_(SIndex sidx, OID src, OID target, Trans t)
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
            throw detectCorruption(e);
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
            try (ResultSet rs = _psTargetOID.executeQuery()) {
                return rs.next() ? new OID(rs.getBytes(1)) : null;
            }
        } catch (SQLException e) {
            DBUtil.close(_psTargetOID);
            _psTargetOID = null;
            throw detectCorruption(e);
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
            throw detectCorruption(e);
        }
    }

    class AliasIterator extends AbstractDBIterator<OID>
    {
        public AliasIterator(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public OID get_() throws SQLException
        {
            return new OID(_rs.getBytes(1));
        }
    }

    PreparedStatement _psListAliases;
    @Override
    public IDBIterator<OID> getAliases_(SOID soid) throws SQLException
    {
        try {
            if (_psListAliases == null) {
                _psListAliases = c().prepareStatement(DBUtil.selectWhere(T_ALIAS,
                        C_ALIAS_SIDX + "=? AND " + C_ALIAS_TARGET_OID + "=?",
                        C_ALIAS_SOURCE_OID));
            }

            _psListAliases.setInt(1, soid.sidx().getInt());
            _psListAliases.setBytes(2, soid.oid().getBytes());

            return new AliasIterator(_psListAliases.executeQuery());
        } catch (SQLException e) {
            DBUtil.close(_psListAliases);
            _psListAliases = null;
            throw detectCorruption(e);
        }
    }
}
