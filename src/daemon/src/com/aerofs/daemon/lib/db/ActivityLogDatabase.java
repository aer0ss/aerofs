/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Set;

import static com.aerofs.daemon.lib.db.CoreSchema.C_AL_DIDS;
import static com.aerofs.daemon.lib.db.CoreSchema.C_AL_IDX;
import static com.aerofs.daemon.lib.db.CoreSchema.C_AL_OID;
import static com.aerofs.daemon.lib.db.CoreSchema.C_AL_PATH;
import static com.aerofs.daemon.lib.db.CoreSchema.C_AL_PATH_TO;
import static com.aerofs.daemon.lib.db.CoreSchema.C_AL_SIDX;
import static com.aerofs.daemon.lib.db.CoreSchema.C_AL_TIME;
import static com.aerofs.daemon.lib.db.CoreSchema.C_AL_TYPE;
import static com.aerofs.daemon.lib.db.CoreSchema.T_AL;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.MOVEMENT_VALUE;

/**
 * When possible, use the ActivityLog class which provides a high-level wrapper for this
 * interface.
 */
public class ActivityLogDatabase extends AbstractDatabase implements IActivityLogDatabase
{
    private final IStores _stores;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public ActivityLogDatabase(CoreDBCW dbcw, IStores stores, IMapSIndex2SID sidx2sid)
    {
        super(dbcw.get());
        _stores = stores;
        _sidx2sid = sidx2sid;
    }

    private static void assertValidity(int activities, @Nullable Path pathTo, Set<DID> dids)
    {
        // these assertions are guaranteed by whoever generates the activities
        assert activities != 0;
        assert !dids.isEmpty();
        assert pathTo != null == Util.test(activities, MOVEMENT_VALUE);
    }

    private PreparedStatement _psAA;
    @Override
    public void insertActivity_(SOID soid, int activities, Path path, @Nullable Path pathTo,
            Set<DID> dids, Trans t) throws SQLException
    {
        assertValidity(activities, pathTo, dids);

        try {
            if (_psAA == null) _psAA = c().prepareStatement("insert into " + T_AL + " ( " +
                    C_AL_SIDX + "," + C_AL_OID + "," + C_AL_TYPE + "," + C_AL_PATH + "," +
                    C_AL_PATH_TO + "," + C_AL_DIDS + "," + C_AL_TIME + ") values (?,?,?,?,?,?,?)");

            _psAA.setInt(1, soid.sidx().getInt());
            _psAA.setBytes(2, soid.oid().getBytes());
            _psAA.setInt(3, activities);
            _psAA.setString(4, path.toStringRelative());
            if (pathTo != null) _psAA.setString(5, pathTo.toStringRelative());
            else _psAA.setNull(5, Types.BLOB);
            _psAA.setBytes(6, convertDIDs(dids));
            _psAA.setLong(7, System.currentTimeMillis());
            _psAA.executeUpdate();
        } catch (SQLException e) {
            DBUtil.close(_psAA);
            _psAA = null;
            throw detectCorruption(e);
        }
    }

    private class DBIterActivityRow extends AbstractDBIterator<ActivityRow>
    {
        DBIterActivityRow(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public boolean next_() throws SQLException
        {
            if (!_rs.next()) return false;

            // ignore entries relative to absent stores
            return _sidx2sid.getNullable_(new SIndex(_rs.getInt(2))) != null || next_();
        }

        @Override
        public ActivityRow get_() throws SQLException
        {
            long idx = _rs.getLong(1);
            SIndex sidx = new SIndex(_rs.getInt(2));
            SID sid = _sidx2sid.get_(_stores.getPhysicalRoot_(sidx));
            OID oid = new OID(_rs.getBytes(3));
            int activities = _rs.getInt(4);
            Path path = Path.fromString(sid, _rs.getString(5));
            String strTo = _rs.getString(6);
            Path pathTo = _rs.wasNull() ? null : Path.fromString(sid, strTo);
            Set<DID> dids = convertDIDs(_rs.getBytes(7));
            long time = _rs.getLong(8);

            assertValidity(activities, pathTo, dids);

            return new ActivityRow(idx, new SOID(sidx, oid), activities, path, pathTo, dids, time);
        }
    }

    private PreparedStatement _psGA;
    @Override
    public IDBIterator<ActivityRow> getActivities_(long idxLast)
            throws SQLException
    {
        try {
            if (_psGA == null) _psGA = c().prepareStatement(
                    "select " + C_AL_IDX + "," + C_AL_SIDX + "," + C_AL_OID + "," +
                    C_AL_TYPE + "," + C_AL_PATH + "," + C_AL_PATH_TO + "," + C_AL_DIDS + "," +
                    C_AL_TIME + " from " + T_AL + " where " + C_AL_IDX + "<? order by " + C_AL_IDX +
                    " desc");

            _psGA.setLong(1, idxLast);
            ResultSet rs = _psGA.executeQuery();

            return new DBIterActivityRow(rs);

        } catch (SQLException e) {
            DBUtil.close(_psGA);
            _psGA = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGT;
    @Override
    public IDBIterator<ActivityRow> getActivitiesAfterIndex_(long idxStart)
            throws SQLException
    {
        try {
            if (_psGT == null) _psGT = c().prepareStatement(
                    "select " + C_AL_IDX + "," + C_AL_SIDX + "," + C_AL_OID + "," +
                    C_AL_TYPE + "," + C_AL_PATH + "," + C_AL_PATH_TO + "," + C_AL_DIDS + "," +
                    C_AL_TIME + " from " + T_AL + " where " + C_AL_IDX + ">? order by " + C_AL_IDX +
                    " asc");

            _psGT.setLong(1, idxStart);
            ResultSet rs = _psGT.executeQuery();

            return new DBIterActivityRow(rs);

        } catch (SQLException e) {
            DBUtil.close(_psGT);
            _psGT = null;
            throw detectCorruption(e);
        }
    }

    /**
     * Convert a concatenation of DID byte arrays to a set of DIDs
     */
    public static Set<DID> convertDIDs(byte[] bs)
    {
        assert bs.length > 0 && bs.length % DID.LENGTH == 0 : bs.length;
        // tree sets are faster for iteration
        Set<DID> set = Sets.newTreeSet();
        for (int i = 0; i < bs.length; i += DID.LENGTH) {
            byte[] bsDID = new byte[DID.LENGTH];
            System.arraycopy(bs, i, bsDID, 0, DID.LENGTH);
            set.add(new DID(bsDID));
        }
        return set;
    }

    /**
     * Convert a set of DIDs to a concatenation of DID byte arrays
     */
    public static byte[] convertDIDs(Set<DID> set)
    {
        byte[] bs = new byte[set.size() * DID.LENGTH];
        int i = 0;
        for (DID did : set) {
            System.arraycopy(did.getBytes(), 0, bs, i++ * DID.LENGTH, DID.LENGTH);
        }
        return bs;
    }

}
