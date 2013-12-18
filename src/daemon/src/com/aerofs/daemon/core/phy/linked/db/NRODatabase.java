package com.aerofs.daemon.core.phy.linked.db;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema.*;

/**
 * NRO stands for Non-Representable Object
 *
 * See docs/design/filesystem_restrictions.md for a high-level overview
 *
 * NB: Inherently Non-Representable Objects may not appear in the DB if a cheap string
 * check shows that the path cannot possibly exist on the underlying physical storage.
 */
public class NRODatabase extends AbstractDatabase
{
    @Inject
    public NRODatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    private final PreparedStatementWrapper _pswCheck = new PreparedStatementWrapper();
    public boolean isNonRepresentable_(SOID soid) throws SQLException
    {
        try {
            PreparedStatement ps = _pswCheck.get();
            if (ps == null) {
                _pswCheck.set(ps = c().prepareStatement(DBUtil.selectWhere(T_NRO,
                        C_NRO_SIDX + "=? and " + C_NRO_OID + "=?",
                        "count(*)")));
            }

            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());

            return DBUtil.binaryCount(ps.executeQuery());
        } catch (SQLException e) {
            _pswCheck.close();
            throw e;
        }
    }

    private final PreparedStatementWrapper _pswGetConflict = new PreparedStatementWrapper();
    public @Nullable OID getConflict_(SOID soid) throws SQLException
    {
        try {
            PreparedStatement ps = _pswGetConflict.get();
            if (ps == null) {
                _pswGetConflict.set(ps = c().prepareStatement(DBUtil.selectWhere(T_NRO,
                        C_NRO_SIDX + "=? and " + C_NRO_OID + "=?",
                        C_NRO_CONFLICT_OID)));
            }

            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());

            ResultSet rs = ps.executeQuery();
            try {
                byte[] b = rs.next() ? rs.getBytes(1) : null;
                return b != null ? new OID(b) : null;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _pswGetConflict.close();
            throw e;
        }
    }

    private final PreparedStatementWrapper _pswSet = new PreparedStatementWrapper();
    public void setNonRepresentable_(SOID soid, @Nullable SOID conflict, Trans t) throws SQLException
    {
        try {
            if (conflict != null) {
                Preconditions.checkState(soid.sidx().equals(conflict.sidx()));
            }
            PreparedStatement ps = _pswSet.get();
            if (ps == null) {
                _pswSet.set(ps = c().prepareStatement(DBUtil.insert(T_NRO,
                        C_NRO_SIDX, C_NRO_OID, C_NRO_CONFLICT_OID)));
            }

            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());
            ps.setBytes(3, conflict != null ? conflict.oid().getBytes() : null);

            Util.verify(ps.executeUpdate() == 1);
        } catch (SQLException e) {
            _pswSet.close();
            throw e;
        }
    }

    private final PreparedStatementWrapper _pswUnset = new PreparedStatementWrapper();
    public void setRepresentable_(SOID soid, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswUnset.get();
            if (ps == null) {
                _pswUnset.set(ps = c().prepareStatement(DBUtil.deleteWhereEquals(T_NRO,
                        C_NRO_SIDX, C_NRO_OID)));
            }

            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());

            ps.executeUpdate();
        } catch (SQLException e) {
            _pswUnset.close();
            throw e;
        }
    }

    private final PreparedStatementWrapper _pswConflicts = new PreparedStatementWrapper();
    public IDBIterator<SOID> getConflicts_(final SOID soid) throws SQLException
    {
        try {
            PreparedStatement ps = _pswConflicts.get();
            if (ps == null) {
                _pswConflicts.set(ps = c().prepareStatement(DBUtil.selectWhere(T_NRO,
                        C_NRO_SIDX + "=? and " + C_NRO_CONFLICT_OID + "=?",
                        C_NRO_OID)));
            }

            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());

            return new AbstractDBIterator<SOID>(ps.executeQuery()) {
                @Override
                public SOID get_() throws SQLException
                {
                    return new SOID(soid.sidx(), new OID(_rs.getBytes(1)));
                }
            };
        } catch (SQLException e) {
            _pswConflicts.close();
            throw e;
        }
    }

    private final PreparedStatementWrapper _pswUpdateConflicts = new PreparedStatementWrapper();
    public void updateConflicts_(SOID soid, OID winner, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswUpdateConflicts.get();
            if (ps == null) {
                _pswUpdateConflicts.set(ps = c().prepareStatement("update " + T_NRO
                        + " set " + C_NRO_CONFLICT_OID + "=?"
                        + " where " + C_NRO_SIDX + "=? and " + C_NRO_CONFLICT_OID + "=?"));
            }

            ps.setBytes(1, winner.getBytes());
            ps.setInt(2, soid.sidx().getInt());
            ps.setBytes(3, soid.oid().getBytes());

            ps.executeUpdate();
        } catch (SQLException e) {
            _pswUpdateConflicts.close();
            throw e;
        }
    }

    // for migration
    private final PreparedStatementWrapper _pswUpdateSIndex = new PreparedStatementWrapper();
    public void updateSIndex_(SOID oldSOID, SIndex sidx, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswUpdateSIndex.get();
            if (ps == null) {
                _pswUpdateSIndex.set(ps = c().prepareStatement("update " + T_NRO
                        + " set " + C_NRO_SIDX + "=?"
                        + " where " + C_NRO_SIDX + "=? and " + C_NRO_OID + "=?"));
            }

            ps.setInt(1, sidx.getInt());
            ps.setInt(2, oldSOID.sidx().getInt());
            ps.setBytes(3, oldSOID.oid().getBytes());

            ps.executeUpdate();
        } catch (SQLException e) {
            _pswUpdateSIndex.close();
            throw e;
        }
    }

    // for aliasing
    private final PreparedStatementWrapper _pswUpdateOID = new PreparedStatementWrapper();
    public void updateOID_(SOID oldSOID, OID newOID, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswUpdateOID.get();
            if (ps == null) {
                _pswUpdateOID.set(ps = c().prepareStatement("update " + T_NRO
                        + " set " + C_NRO_OID + "=?"
                        + " where " + C_NRO_SIDX + "=? and " + C_NRO_OID + "=?"));
            }

            ps.setBytes(1, newOID.getBytes());
            ps.setInt(2, oldSOID.sidx().getInt());
            ps.setBytes(3, oldSOID.oid().getBytes());

            ps.executeUpdate();
        } catch (SQLException e) {
            _pswUpdateOID.close();
            throw e;
        }
    }
}
