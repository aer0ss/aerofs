package com.aerofs.daemon.lib.db;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.google.common.base.Preconditions.checkState;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Set;
import java.util.SortedMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/*
 * When possible, use the DirectoryService class which provides a high-level wrapper for this class.
 */
public class MetaDatabase extends AbstractDatabase implements IMetaDatabase, IMetaDatabaseWalker
{
    @Inject
    public MetaDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
    }

    PreparedStatement _psGetChild;
    @Override
    public @Nullable OID getChild_(SIndex sidx, OID parent, String name)
            throws SQLException
    {
        assert parent != null;
        try {
            if (_psGetChild == null) _psGetChild = c()
                    .prepareStatement("select " + C_OA_OID + " from "
                            + T_OA + " where " + C_OA_SIDX + "=? and "
                            + C_OA_PARENT + "=? and " + C_OA_NAME + "=?");
            _psGetChild.setInt(1, sidx.getInt());
            _psGetChild.setBytes(2, parent.getBytes());
            _psGetChild.setString(3, name);
            ResultSet rs = _psGetChild.executeQuery();
            try {
                if (rs.next()) {
                    return new OID(rs.getBytes(1));
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGetChild);
            _psGetChild = null;
            throw detectCorruption(e);
        }
    }

    //private PreparedStatement and userpin to null for directories
    private PreparedStatement _psInsOA;

    @Override
    public void insertOA_(SIndex sidx, OID oid, OID oidParent, String name, OA.Type type, int flags,
            Trans t)
        throws SQLException, ExAlreadyExist
    {
        try {
            if (_psInsOA == null) _psInsOA = c()
                .prepareStatement("insert into " + T_OA + "("
                    + C_OA_SIDX + ","
                    + C_OA_PARENT + ","
                    + C_OA_NAME + ","
                    + C_OA_OID + ","
                    + C_OA_TYPE + ","
                    + C_OA_FLAGS + ")"
                    + " values (?,?,?,?,?,?)");
            _psInsOA.setInt(1, sidx.getInt());
            _psInsOA.setBytes(2, oidParent.getBytes());
            _psInsOA.setString(3, name);
            _psInsOA.setBytes(4, oid.getBytes());
            _psInsOA.setInt(5, type.ordinal());
            _psInsOA.setInt(6, flags);
            _psInsOA.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psInsOA);
            _psInsOA = null;
            // must be called *after* closing the statement
            _dbcw.throwOnConstraintViolation(e);
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psInsCA;

    @Override
    public void insertCA_(SOID soid, KIndex kidx, Trans t)
            throws SQLException
    {
        try {
            if (_psInsCA == null) _psInsCA = c().prepareStatement("insert into "
                    + T_CA + "(" + C_CA_SIDX + "," + C_CA_OID + "," + C_CA_KIDX
                    + "," + C_CA_LENGTH + ") " + "values (?,?,?,0)");
            _psInsCA.setInt(1, soid.sidx().getInt());
            _psInsCA.setBytes(2, soid.oid().getBytes());
            _psInsCA.setInt(3, kidx.getInt());
            _psInsCA.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psInsCA);
            _psInsCA = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDelCA;

    @Override
    public void deleteCA_(SOID soid, KIndex kidx, Trans t)
            throws SQLException
    {
        try {
            if (_psDelCA == null) _psDelCA = c().prepareStatement("delete from "
                    + T_CA + " where " + C_CA_SIDX + "=? and " + C_CA_OID
                    + "=? and " + C_CA_KIDX + "=?");
            _psDelCA.setInt(1, soid.sidx().getInt());
            _psDelCA.setBytes(2, soid.oid().getBytes());
            _psDelCA.setInt(3, kidx.getInt());
            Util.verify(_psDelCA.executeUpdate() == 1);

        } catch (SQLException e) {
            DBUtil.close(_psDelCA);
            _psDelCA = null;
            throw detectCorruption(e);
        }
    }

    private void setOAInt_(SOID soid, int v, PreparedStatementWrapper psw, Trans t)
            throws SQLException
    {
        PreparedStatement ps = psw.get(c());
        ps.setInt(1, v);
        ps.setInt(2, soid.sidx().getInt());
        ps.setBytes(3, soid.oid().getBytes());
        Util.verify(ps.executeUpdate() == 1);
    }

    private PreparedStatement _psSOAPNC;

    @Override
    public void setOAParentAndName_(SIndex sidx, OID oid, OID parent,
            String name, Trans t) throws SQLException, ExAlreadyExist
    {
        try {
            if (_psSOAPNC == null) _psSOAPNC = c()
                    .prepareStatement("update " + T_OA + " set "
                            + C_OA_PARENT + "=?," + C_OA_NAME + "=?"
                            + " where " + C_OA_SIDX + "=? and "
                            + C_OA_OID + "=?");
            _psSOAPNC.setBytes(1, parent.getBytes());
            _psSOAPNC.setString(2, name);
            _psSOAPNC.setInt(3, sidx.getInt());
            _psSOAPNC.setBytes(4, oid.getBytes());
            Util.verify(_psSOAPNC.executeUpdate() == 1);

        } catch (SQLException e) {
            DBUtil.close(_psSOAPNC);
            _psSOAPNC = null;
            // must be called *after* closing the statement
            _dbcw.throwOnConstraintViolation(e);
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswSOAP = prepareOAUpdate(C_OA_PARENT);
    @Override
    public void setOAParent_(SIndex sidx, OID oid, OID parent, Trans t)
            throws SQLException, ExAlreadyExist
    {
        try {
            int n = setOABlob_(_pswSOAP, new SOID(sidx, oid), parent.getBytes(), t);
            Util.verify(n == 1);
        } catch (SQLException e) {
            _pswSOAP.close();
            _dbcw.throwOnConstraintViolation(e);
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswROAOID = prepareOAUpdate(C_OA_OID);
    @Override
    public void replaceOAOID_(SIndex sidx, OID oidOld, OID oidNew, Trans t)
            throws SQLException, ExAlreadyExist {
        try {
            int n = setOABlob_(_pswROAOID, new SOID(sidx, oidOld), oidNew.getBytes(), t);
            Util.verify(n == 1);
        } catch (SQLException e) {
            _pswROAOID.close();
            _dbcw.throwOnConstraintViolation(e);
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswRPIC = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_OA, C_OA_SIDX + "=? and " + C_OA_PARENT +"=?", C_OA_PARENT));
    @Override
    public void replaceParentInChildren_(SIndex sidx, OID oldParent, OID newParent, Trans t)
            throws SQLException, ExAlreadyExist
    {
        try {
            PreparedStatement ps = _pswRPIC.get(c());
            ps.setBytes(1, newParent.getBytes());
            ps.setInt(2, sidx.getInt());
            ps.setBytes(3, oldParent.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswRPIC.close();
            _dbcw.throwOnConstraintViolation(e);
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswRCA = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_CA, C_CA_SIDX + "=? and " + C_CA_OID +"=?", C_CA_OID));
    @Override
    public void replaceCA_(SIndex sidx, OID oldParent, OID newParent, Trans t)
            throws SQLException
    {
        try {
            PreparedStatement ps = _pswRCA.get(c());
            ps.setBytes(1, newParent.getBytes());
            ps.setInt(2, sidx.getInt());
            ps.setBytes(3, oldParent.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswRCA.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswSOAF = prepareOAUpdate(C_OA_FLAGS);
    @Override
    public void setOAFlags_(SOID soid, int flags, Trans t) throws SQLException
    {
        try {
            setOAInt_(soid, flags & OA.FLAG_DB_MASK, _pswSOAF, t);
        } catch (SQLException e) {
            _pswSOAF.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psSCALM;
    @Override
    public void setCA_(SOID soid, KIndex kidx, long len, long mtime, @Nullable ContentHash h,
            Trans t) throws SQLException
    {
        try {
            if (_psSCALM == null) _psSCALM = c().prepareStatement(
                    "update " + T_CA + " set " + C_CA_LENGTH + "=?, " +
                    C_CA_MTIME + "=?," + C_CA_HASH + "=? where " +
                    C_CA_SIDX + "=? and " + C_CA_OID + "=? and " +
                    C_CA_KIDX + "=?");
            _psSCALM.setLong(1, len);
            _psSCALM.setLong(2, mtime);
            if (h == null) _psSCALM.setNull(3, Types.BLOB);
            else _psSCALM.setBytes(3, h.getBytes());
            _psSCALM.setInt(4, soid.sidx().getInt());
            _psSCALM.setBytes(5, soid.oid().getBytes());
            _psSCALM.setInt(6, kidx.getInt());
            Util.verify(_psSCALM.executeUpdate() == 1);
        } catch (SQLException e) {
            DBUtil.close(_psSCALM);
            _psSCALM = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psSCAH;
    @Override
    public void setCAHash_(SOID soid, KIndex kidx, @Nonnull ContentHash h, Trans t)
        throws SQLException
    {
        try {
            if (_psSCAH == null) _psSCAH = c().prepareStatement(
                    "update " + T_CA + " set " +
                    C_CA_HASH + "=? where " +
                    C_CA_SIDX + "=? and " +
                    C_CA_OID + "=? and " +
                    C_CA_KIDX + "=?");

            _psSCAH.setBytes(1, h.getBytes());
            _psSCAH.setInt(2, soid.sidx().getInt());
            _psSCAH.setBytes(3, soid.oid().getBytes());
            _psSCAH.setInt(4, kidx.getInt());

            int updates = _psSCAH.executeUpdate();
            assert updates == 1 : Joiner.on(' ').join(updates, soid, kidx);
        } catch (SQLException e) {
            DBUtil.close(_psSCAH);
            _psSCAH = null;
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswSFID = prepareOAUpdate(C_OA_FID);
    @Override
    public void setFID_(SOID soid, @Nullable FID fid, Trans t) throws SQLException
    {
        try {
            int n = setOABlob_(_pswSFID, soid, fid == null ? null : fid.getBytes(), t);
            Util.verify(n == 1);
        } catch (SQLException e) {
            _pswSFID.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGetCA;
    private SortedMap<KIndex, CA> getCAs_(SOID soid) throws SQLException
    {
        try {
            if (_psGetCA == null) _psGetCA = c().prepareStatement("select "
                    + C_CA_LENGTH + "," + C_CA_KIDX + "," + C_CA_MTIME
                    + " from " + T_CA + " where " + C_CA_SIDX + "=? and "
                    + C_CA_OID + "=?");
            _psGetCA.setInt(1, soid.sidx().getInt());
            _psGetCA.setBytes(2, soid.oid().getBytes());
            ResultSet rs = _psGetCA.executeQuery();
            try {
                SortedMap<KIndex, CA> cas = Maps.newTreeMap();
                while (rs.next()) {
                    long len = rs.getLong(1);
                    KIndex kidx = new KIndex(rs.getInt(2));

                    // getLong() returns 0 if the value is SQL NULL.
                    long mtime = rs.getLong(3);

                    cas.put(kidx, new CA(len, mtime));
                }
                return cas;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGetCA);
            _psGetCA = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGCAH;
    @Override
    public @Nullable ContentHash getCAHash_(SOID soid, KIndex kidx)
            throws SQLException
    {
        try {
            if (_psGCAH == null) _psGCAH = c().prepareStatement(
                "select " + C_CA_HASH + " from " + T_CA + " where " +
                C_CA_SIDX + "=? and " +
                C_CA_OID + "=? and " +
                C_CA_KIDX + "=?");

            _psGCAH.setInt(1, soid.sidx().getInt());
            _psGCAH.setBytes(2, soid.oid().getBytes());
            _psGCAH.setInt(3, kidx.getInt());

            ResultSet rs = _psGCAH.executeQuery();
            try {
                byte[] hash;
                if (rs.next() && (hash = rs.getBytes(1)) != null) {
                    return new ContentHash(hash);
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGCAH);
            _psGCAH = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGetOA;
    @Override
    public @Nullable OA getOA_(SOID soid) throws SQLException
    {
        try {
            if (_psGetOA == null) _psGetOA = c().prepareStatement("select "
                    + C_OA_PARENT + "," + C_OA_NAME + "," + C_OA_TYPE + ","
                    + C_OA_FLAGS + "," + C_OA_FID + " from "
                    + T_OA + " where " + C_OA_SIDX + "=? and " + C_OA_OID
                    + "=?");

            _psGetOA.setInt(1, soid.sidx().getInt());
            _psGetOA.setBytes(2, soid.oid().getBytes());
            ResultSet rs = _psGetOA.executeQuery();
            try {
                if (rs.next()) {
                    OID parent = new OID(rs.getBytes(1));
                    String name = rs.getString(2);
                    OA.Type type = OA.Type.valueOf(rs.getInt(3));
                    int flags = rs.getInt(4);
                    byte[] bs = rs.getBytes(5);
                    FID fid = bs == null ? null : new FID(bs);

                    assert !rs.next();

                    switch(type) {
                    case FILE:
                        return OA.createFile(soid, parent, name, getCAs_(soid), flags, fid);
                    default:
                        return OA.createNonFile(soid, parent, name, type, flags, fid);
                    }

                } else {
                    return null;
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGetOA);
            _psGetOA = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psFID2SOID;
    @Override
    public @Nullable SOID getSOID_(FID fid) throws SQLException
    {
        try {
            if (_psFID2SOID == null) _psFID2SOID = c().prepareStatement(
                    "select " + C_OA_SIDX + "," + C_OA_OID +
                    " from " + T_OA +
                    " where " + C_OA_FID + "=?");

            _psFID2SOID.setBytes(1, fid.getBytes());
            ResultSet rs = _psFID2SOID.executeQuery();
            try {
                if (rs.next()) {
                    SIndex sidx = new SIndex(rs.getInt(1));
                    OID oid = new OID(rs.getBytes(2));
                    // There is a uniqueness constraint on FIDs.
                    assert !rs.next();
                    return new SOID(sidx, oid);
                } else {
                    return null;
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psFID2SOID);
            _psFID2SOID = null;
            throw detectCorruption(e);
        }
    }

    private static class DBIterNonMasterBranches extends
            AbstractDBIterator<SOKID>
    {
        DBIterNonMasterBranches(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public SOKID get_() throws SQLException
        {
            SIndex sidx = new SIndex(_rs.getInt(1));
            OID oid = new OID(_rs.getBytes(2));
            KIndex kidx = new KIndex(_rs.getInt(3));
            return new SOKID(sidx, oid, kidx);
        }
    }

    @Override
    public IDBIterator<SOKID> getAllNonMasterBranches_()
        throws SQLException
    {
        Statement s = null;
        try {
            // this method is not called requently so we don't prepare the stmt
            s = c().createStatement();
            ResultSet rs = s.executeQuery("select "
                    + C_CA_SIDX + "," + C_CA_OID + "," + C_CA_KIDX +
                    " from " + T_CA +
                    " where " + C_CA_KIDX + "!=" + KIndex.MASTER);

            return new DBIterNonMasterBranches(rs);

        } catch (SQLException e) {
            DBUtil.close(s);
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswHasChildren = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_OA, C_OA_SIDX + "=? and " + C_OA_PARENT + "=?", "1") + " limit 1");
    @Override
    public boolean hasChildren_(SOID parent) throws SQLException
    {
        try {
            PreparedStatement ps = _pswHasChildren.get(c());
            ps.setInt(1, parent.sidx().getInt());
            ps.setBytes(2, parent.oid().getBytes());
            ResultSet rs = ps.executeQuery();
            try {
                return rs.next();
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _pswHasChildren.close();
            throw detectCorruption(e);
        }
    }

    @Override
    public Set<OID> getChildren_(SOID parent) throws SQLException
    {
        IDBIterator<OID> it = listChildren_(parent);
        try {
            // Most callers of this method simply iterate over the set, so if performance is
            // an issue, consider using a LinkedHashSet
            Set<OID> children = Sets.newHashSet();
            while (it.next_()) children.add(it.get_());
            return children;
        } catch (SQLException e) {
            throw detectCorruption(e);
        } finally {
            it.close_();
        }
    }

    private PreparedStatementWrapper _pswGetChildren = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_OA, C_OA_SIDX + "=? and " + C_OA_PARENT + "=?", C_OA_OID));
    @Override
    public IDBIterator<OID> listChildren_(SOID soid) throws SQLException
    {
        try {
            PreparedStatement ps = _pswGetChildren.get(c());
            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());
            ResultSet rs = ps.executeQuery();
            final boolean rootParent = soid.oid().isRoot();
            return new AbstractDBIterator<OID>(rs) {
                OID oid;
                @Override
                public boolean next_() throws SQLException
                {
                    do {
                        if (!_rs.next()) return false;
                        oid = new OID(_rs.getBytes(1));
                    } while (rootParent && oid.isRoot());
                    return true;
                }

                @Override
                public OID get_() throws SQLException
                {
                    return oid;
                }
            };
        } catch (SQLException e) {
            _pswGetChildren.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswGetTypedChildren = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_OA,
                    C_OA_SIDX + "=? and " + C_OA_PARENT + "=? and " + C_OA_FLAGS + "=0",
                    C_OA_OID, C_OA_NAME, C_OA_TYPE));
    @Override
    public IDBIterator<TypeNameOID> getTypedChildren_(SIndex sidx, OID parent)
            throws SQLException
    {
        try {
            PreparedStatement ps = _pswGetTypedChildren.get(c());
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, parent.getBytes());
            ResultSet rs = ps.executeQuery();
            try {
                final boolean rootParent = parent.isRoot();
                return new AbstractDBIterator<TypeNameOID>(rs) {
                    OID oid;
                    @Override
                    public boolean next_() throws SQLException
                    {
                        do {
                            if (!_rs.next()) return false;
                            oid = new OID(_rs.getBytes(1));
                        } while (rootParent && oid.isRoot());
                        return true;
                    }

                    @Override
                    public TypeNameOID get_() throws SQLException
                    {
                        return new TypeNameOID(_rs.getString(2), oid, Type.valueOf(_rs.getInt(3)));
                    }
                };
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _pswGetTypedChildren.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDeleteOA;
    @Override
    public void deleteOA_(SIndex sidx, OID alias, Trans t)
            throws SQLException
    {
        try {
            if (_psDeleteOA == null) _psDeleteOA =
                    c().prepareStatement("delete from " + T_OA + " where " +
                    C_OA_SIDX + "=? and " +
                    C_OA_OID + "=?");
            _psDeleteOA.setInt(1, sidx.getInt());
            _psDeleteOA.setBytes(2, alias.getBytes());

            _psDeleteOA.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psDeleteOA);
            _psDeleteOA = null;
            throw detectCorruption(e);
        }
    }

    private static PreparedStatementWrapper prepareOAUpdate(String column)
    {
        return new PreparedStatementWrapper(DBUtil.updateWhere(T_OA,
                    C_OA_SIDX + "=? and " + C_OA_OID + "=?",
                    column));
    }

    /**
     * Write a blob to a given column of the object attribute table
     *
     * @param psw PreparedStatement wrapper to be used for the DB update
     * @param soid Object for which to update the blob
     * @param blob byte array containing the blob to write
     *
     * The prepared statement will be automatically initialized by this method. The use of a wrapper
     * is necessary as Java does not support passing arguments by reference.
     */
    private int setOABlob_(PreparedStatementWrapper psw, SOID soid,
            @Nullable byte[] blob, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = psw.get(c());
            ps.setInt(2, soid.sidx().getInt());
            ps.setBytes(3, soid.oid().getBytes());
            if (blob == null) {
                ps.setNull(1, Types.BLOB);
            } else {
                ps.setBytes(1, blob);
            }
            int affectedRows = ps.executeUpdate();
            // NOTE: Silently ignore missing SOID (should we use a boolean return flag instead?)
            assert affectedRows <= 1;
            return affectedRows;
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    @Override
    public void deleteOAsAndCAsForStore_(SIndex sidx, Trans t)
            throws SQLException
    {
        StoreDatabase.deleteRowsInTablesForStore_(
                ImmutableMap.of(T_OA, C_OA_SIDX, T_CA, C_CA_SIDX), sidx, c(), t);
    }

    private PreparedStatement _psGetBytesUsed;
    @Override
    public long getBytesUsed_(SIndex sidx) throws SQLException
    {
        try {
            if (_psGetBytesUsed == null) _psGetBytesUsed = c()
                    .prepareStatement("select sum(" + C_CA_LENGTH + ") from "
                            + T_CA + " where " + C_CA_SIDX + "=?");

            _psGetBytesUsed.setInt(1, sidx.getInt());

            ResultSet rs = _psGetBytesUsed.executeQuery();
            try {
                checkState(rs.next());
                long ret = rs.getLong(1);
                checkState(!rs.next());
                return ret;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGetBytesUsed);
            _psGetBytesUsed = null;
            throw e;
        }
    }
}
