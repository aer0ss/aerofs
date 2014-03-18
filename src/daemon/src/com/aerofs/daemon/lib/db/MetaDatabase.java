package com.aerofs.daemon.lib.db;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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

    private void setOAInt_(SOID soid, String column, int v, PreparedStatementWrapper psw, Trans t)
            throws SQLException
    {
        PreparedStatement ps = prepareOAUpdate(psw, column, soid);
        ps.setInt(1, v);
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

    private final PreparedStatementWrapper _pswSOAP = new PreparedStatementWrapper();
    @Override
    public void setOAParent_(SIndex sidx, OID oid, OID parent, Trans t)
            throws SQLException, ExAlreadyExist
    {
        try {
            int n = setOABlob_(_pswSOAP, new SOID(sidx, oid), C_OA_PARENT, parent.getBytes(), t);
            Util.verify(n == 1);
        } catch (SQLException e) {
            _pswSOAP.close();
            _dbcw.throwOnConstraintViolation(e);
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswROAOID = new PreparedStatementWrapper();
    @Override
    public void replaceOAOID_(SIndex sidx, OID oidOld, OID oidNew, Trans t)
            throws SQLException, ExAlreadyExist {
        try {
            int n = setOABlob_(_pswROAOID, new SOID(sidx, oidOld), C_OA_OID, oidNew.getBytes(), t);
            Util.verify(n == 1);
        } catch (SQLException e) {
            _pswROAOID.close();
            _dbcw.throwOnConstraintViolation(e);
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswRPIC = new PreparedStatementWrapper();
    @Override
    public void replaceParentInChildren_(SIndex sidx, OID oldParent, OID newParent, Trans t)
            throws SQLException, ExAlreadyExist
    {
        try {
            PreparedStatement ps = _pswRPIC.get();
            if (ps == null) {
                _pswRPIC.set(ps = c().prepareStatement(DBUtil.updateWhere(T_OA,
                        C_OA_SIDX + "=? and " + C_OA_PARENT +"=?",
                        C_OA_PARENT)));
            }
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

    private final PreparedStatementWrapper _pswRCA = new PreparedStatementWrapper();
    @Override
    public void replaceCA_(SIndex sidx, OID oldParent, OID newParent, Trans t)
            throws SQLException
    {
        try {
            PreparedStatement ps = _pswRCA.get();
            if (ps == null) {
                _pswRCA.set(ps = c().prepareStatement(DBUtil.updateWhere(T_CA,
                        C_CA_SIDX + "=? and " + C_CA_OID +"=?",
                        C_CA_OID)));
            }
            ps.setBytes(1, newParent.getBytes());
            ps.setInt(2, sidx.getInt());
            ps.setBytes(3, oldParent.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswRCA.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswSOAF = new PreparedStatementWrapper();
    @Override
    public void setOAFlags_(SOID soid, int flags, Trans t) throws SQLException
    {
        try {
            setOAInt_(soid, C_OA_FLAGS, flags, _pswSOAF, t);
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

    private final PreparedStatementWrapper _pswSFID = new PreparedStatementWrapper();
    @Override
    public void setFID_(SOID soid, @Nullable FID fid, Trans t) throws SQLException
    {
        try {
            int n = setOABlob_(_pswSFID, soid, C_OA_FID, fid == null ? null : fid.getBytes(), t);
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

    private PreparedStatement _psGetChildren;
    @Override
    public Set<OID> getChildren_(SOID parent) throws SQLException
    {
        try {
            if (_psGetChildren == null) _psGetChildren = c()
                    .prepareStatement(
                            "select " + C_OA_OID + " from " + T_OA + " where " + C_OA_SIDX +
                                    "=? and " + C_OA_PARENT + "=?");
            _psGetChildren.setInt(1, parent.sidx().getInt());
            _psGetChildren.setBytes(2, parent.oid().getBytes());
            ResultSet rs = _psGetChildren.executeQuery();
            try {
                boolean rootParent = parent.oid().isRoot();
                // Most callers of this method simply iterate over the set, so if performance is
                // an issue, consider using a LinkedHashSet
                Set<OID> children = new HashSet<OID>();
                while (rs.next()) {
                    OID oid = new OID(rs.getBytes(1));
                    if (rootParent && oid.isRoot()) continue;
                    children.add(oid);
                }
                return children;
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            DBUtil.close(_psGetChildren);
            _psGetChildren = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGetTypedChildren;
    @Override
    public Collection<TypeNameOID> getTypedChildren_(SIndex sidx, OID parent)
            throws SQLException
    {
        try {
            if (_psGetTypedChildren == null) _psGetTypedChildren = c()
                    .prepareStatement(DBUtil.selectWhere(T_OA,
                            C_OA_SIDX + "=? and " + C_OA_PARENT + "=? and " + C_OA_FLAGS + "=?",
                            C_OA_OID, C_OA_NAME, C_OA_TYPE));
            _psGetTypedChildren.setInt(1, sidx.getInt());
            _psGetTypedChildren.setBytes(2, parent.getBytes());
            _psGetTypedChildren.setInt(3, 0);
            ResultSet rs = _psGetTypedChildren.executeQuery();
            try {
                boolean rootParent = parent.isRoot();
                // Most callers of this method simply iterate over the set, so if performance is
                // an issue, consider using a LinkedHashSet
                Collection<TypeNameOID> children = Lists.newArrayList();
                while (rs.next()) {
                    OID oid = new OID(rs.getBytes(1));
                    if (rootParent && oid.isRoot()) continue;
                    children.add(new TypeNameOID(rs.getString(2), oid, Type.valueOf(rs.getInt(3))));
                }
                return children;
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            DBUtil.close(_psGetTypedChildren);
            _psGetTypedChildren = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGSCC;
    @Override
    public int getSyncableChildCount_(SOID soid) throws SQLException
    {
        try {
            if (_psGSCC == null) {
                _psGSCC = c().prepareStatement(DBUtil.selectWhere(T_OA,
                        C_OA_SIDX + "=? and " + C_OA_PARENT + "=? and " + C_OA_FLAGS + "=0",
                        "count(*)"));
            }
            _psGSCC.setInt(1, soid.sidx().getInt());
            _psGSCC.setBytes(2, soid.oid().getBytes());
            ResultSet rs = _psGSCC.executeQuery();
            // store ROOTs are always their own parent so we have to adjust the result of query
            int off = soid.oid().isRoot() ? -1 : 0;
            try {
                return DBUtil.count(rs) + off;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            DBUtil.close(_psGSCC);
            _psGSCC = null;
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

    private final PreparedStatementWrapper _pswGetSync = new PreparedStatementWrapper();
    @Override
    public @Nonnull BitVector getSyncStatus_(SOID soid) throws SQLException
    {
        byte[] d = getOABlob_(_pswGetSync, soid, C_OA_SYNC);
        return d != null ? new BitVector(8 * d.length, d) : new BitVector();
    }

    private final PreparedStatementWrapper _pswSetSync = new PreparedStatementWrapper();
    @Override
    public void setSyncStatus_(SOID soid, BitVector status, Trans t) throws SQLException
    {
        setOABlob_(_pswSetSync, soid, C_OA_SYNC, status.data(), t);
    }

    private final PreparedStatementWrapper _pswGetAgSync = new PreparedStatementWrapper();
    @Override
    public @Nonnull CounterVector getAggregateSyncStatus_(SOID soid) throws SQLException
    {
        byte[] d = getOABlob_(_pswGetAgSync, soid, C_OA_AG_SYNC);
        return d != null ? CounterVector.fromByteArrayCompressed(d) : new CounterVector();
    }

    private final PreparedStatementWrapper _pswSetAgSync = new PreparedStatementWrapper();
    @Override
    public void setAggregateSyncStatus_(SOID soid, CounterVector agstat, Trans t)
            throws SQLException
    {
        setOABlob_(_pswSetAgSync, soid, C_OA_AG_SYNC, agstat.toByteArrayCompressed(), t);
    }

    private PreparedStatement prepareOASelect(PreparedStatementWrapper psw, String column,
            SOID soid) throws SQLException
    {
        if (psw.get() == null) {
            psw.set(c().prepareStatement(DBUtil.selectWhere(T_OA,
                    C_OA_SIDX + "=? and " + C_OA_OID + "=?",
                    column)));
        }
        PreparedStatement ps = psw.get();
        ps.setInt(1, soid.sidx().getInt());
        ps.setBytes(2, soid.oid().getBytes());
        return ps;
    }

    private PreparedStatement prepareOAUpdate(PreparedStatementWrapper psw, String column,
            SOID soid) throws SQLException
    {
        if (psw.get() == null) {
            psw.set(c().prepareStatement(DBUtil.updateWhere(T_OA,
                    C_OA_SIDX + "=? and " + C_OA_OID + "=?",
                    column)));
        }
        PreparedStatement ps = psw.get();
        ps.setInt(2, soid.sidx().getInt());
        ps.setBytes(3, soid.oid().getBytes());
        return ps;
    }

    /**
     * Read a blob from a given column of the object attribute table
     *
     * @param psw PreparedStatement wrapper to be used for the DB lookup
     * @param soid Object for which to lookup the blob
     * @param column name of the column of interest
     * @return a byte array containing the blob of interest, null if not found
     *
     * Note: the return value will be null if the given {@code soid} is not present in the DB,
     * if the value stored in the DB is an explicit NULL or if it is an empty byte array.
     *
     * The prepared statement will be automatically initialized by this method. The use of a wrapper
     * is necessary as Java does not support passing arguments by reference.
     */
    private @Nullable byte[] getOABlob_(PreparedStatementWrapper psw, SOID soid, String column) throws SQLException
    {
        try {
            PreparedStatement ps = prepareOASelect(psw, column, soid);
            ResultSet rs = ps.executeQuery();
            byte[] blob = null;
            try {
                if (rs.next()) {
                    blob = rs.getBytes(1);
                    Util.verify(!rs.next()); // and only one entry...
                }
            } finally {
                rs.close();
            }
            return blob;
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    /**
     * Write a blob to a given column of the object attribute table
     *
     * @param psw PreparedStatement wrapper to be used for the DB update
     * @param soid Object for which to update the blob
     * @param column name of the column of interest
     * @param blob byte array containing the blob to write
     *
     * The prepared statement will be automatically initialized by this method. The use of a wrapper
     * is necessary as Java does not support passing arguments by reference.
     */
    private int setOABlob_(PreparedStatementWrapper psw, SOID soid, String column,
            @Nullable byte[] blob, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = prepareOAUpdate(psw, column, soid);
            if (blob == null) {
                ps.setNull(1, Types.BLOB);
            } else {
                ps.setBytes(1, blob);
            }
            int affectedRows = psw.get().executeUpdate();
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
}
