package com.aerofs.fsck;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import com.aerofs.base.Loggers;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.FID;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

public class DBCheckFID
{
    private static final Logger l = Loggers.getLogger(DBCheckFID.class);

    private final IDBCW _dbcw;

    // TODO close these statements after use
    private PreparedStatement _psListChildren;
    private PreparedStatement _psTestAbsence;
    private PreparedStatement _psTestDuplicateFID;

    /**
     * A class that captures the arguments to be passed into {@code checkRecursive_()}.
     */
    private static class CheckArgs {
        final SIndex _sidx;
        final OID _oid;
        final FID _fid;
        final String _name;
        final OA.Type _type;
        final int _flags;

        public CheckArgs(SIndex sidx, OID oid, FID fid, String name, OA.Type type, int flags)
        {
            _sidx = sidx;
            _oid = oid;
            _fid = fid;
            _name = name;
            _type = type;
            _flags = flags;
        }
    }

    @Inject
    public DBCheckFID(CoreDBCW dbcw)
    {
        _dbcw = dbcw.get();
    }

    /**
     * all hosted or off-cache files must have null FIDs
     * all linked, in-cache files must have non-null FIDs
     */
    public void check_(boolean fix, InOutArg<Boolean> okay)
        throws SQLException
    {
        // prepare statements
        _psListChildren = _dbcw.getConnection().prepareStatement(
                "select " + C_OA_OID + "," + C_OA_FID + "," + C_OA_NAME +
                    "," + C_OA_TYPE + "," + C_OA_FLAGS + " from " + T_OA +
                " where " + C_OA_SIDX + "=? and " + C_OA_PARENT + "=?" +
                " and " + C_OA_PARENT + " <> " + C_OA_OID);
        _psTestAbsence = _dbcw.getConnection().prepareStatement(
                "select count(*) from " + T_CA +
                " where " + C_CA_SIDX + "=? and " + C_CA_OID + "=?");
        _psTestDuplicateFID = _dbcw.getConnection().prepareStatement(
                "select count(*) from " + T_OA +
                " where " + C_OA_FID + "=?");

        ArrayList<SIndex> sidcs = new ArrayList<SIndex>();
        ResultSet rs = _dbcw.getConnection().createStatement().executeQuery(
                "select " + C_STORE_SIDX + " from " + T_STORE);
        while (rs.next()) sidcs.add(new SIndex(rs.getInt(1)));

        // list children under store roots
        ArrayList<CheckArgs> args = new ArrayList<CheckArgs>();
        for (SIndex sidx : sidcs) {
            _psListChildren.setInt(1, sidx.getInt());
            _psListChildren.setBytes(2, OID.ROOT.getBytes());
            rs = _psListChildren.executeQuery();
            while (rs.next()) {
                String name = rs.getString(3);
                byte[] fid = rs.getBytes(2);
                args.add(new CheckArgs(sidx, new OID(rs.getBytes(1)),
                        fid == null ? null : new FID(fid), name, OA.Type.values()[rs.getInt(4)],
                        rs.getInt(5)));
            }
        }

        // must not call checkRecursive while iterating the result set because that method also uses
        // _psListChildren.
        for (CheckArgs a : args) {
            checkRecursive_(a._sidx, a._oid, a._fid, a._name, a._type,
                    Util.test(a._flags, OA.FLAG_EXPELLED_ORG), fix, okay);
        }
    }

    private static void error(String expected, SIndex sidx, OID oid, FID fid,
            String name, InOutArg<Boolean> okay)
    {
        DBChecker.error(expected, new SOID(sidx, oid) + " " + name + ": " + fid, okay);
    }

    private void checkChildren_(SIndex sidx, OID oid, boolean expelled,
            boolean fix, InOutArg<Boolean> okay)
        throws SQLException
    {
        _psListChildren.setInt(1, sidx.getInt());
        _psListChildren.setBytes(2, oid.getBytes());
        ResultSet rs = _psListChildren.executeQuery();
        ArrayList<CheckArgs> args = new ArrayList<CheckArgs>();
        while (rs.next()) {
            byte[] fidChild = rs.getBytes(2);
            args.add(new CheckArgs(sidx, new OID(rs.getBytes(1)),
                    fidChild == null ? null : new FID(fidChild), rs.getString(3),
                    OA.Type.values()[rs.getInt(4)], rs.getInt(5)));
        }

        // must not call checkRecursive while iterating the result set because that method also uses
        // _psListChildren.
        for (CheckArgs a : args) {
            checkRecursive_(a._sidx, a._oid, a._fid, a._name, a._type,
                    expelled || Util.test(a._flags, OA.FLAG_EXPELLED_ORG), fix, okay);
        }
    }

    private void checkRecursive_(SIndex sidx, OID oid, FID fid, String name, OA.Type type,
            boolean expelled, boolean fix, InOutArg<Boolean> okay)
            throws SQLException
    {
        if (fid != null) {
            _psTestDuplicateFID.setBytes(1, fid.getBytes());
            ResultSet rs = _psTestDuplicateFID.executeQuery();
            Util.verify(rs.next());
            if (rs.getInt(1) != 1) error("dupliate FIDs", sidx, oid, fid, name, okay);
            assert !rs.next();
        }

        if (expelled && fid != null) {
            error("expelled object must have null FID", sidx, oid, fid, name, okay);
        }

        if (type == OA.Type.DIR) {
            if (!expelled && fid == null) {
                error("admitted dir must have non-null FID", sidx, oid, fid, name, okay);
            }
            checkChildren_(sidx, oid, false, fix, okay);

        } else if (type == OA.Type.FILE) {
            _psTestAbsence.setInt(1, sidx.getInt());
            _psTestAbsence.setBytes(2, oid.getBytes());
            ResultSet rs = _psTestAbsence.executeQuery();
            Util.verify(rs.next());
            boolean absent = rs.getInt(1) == 0;
            if (expelled && !absent) {
                error("expelled files must be absent", sidx, oid, fid, name, okay);
            }

            if (absent && fid != null) {
                error("absent files must have null FID", sidx, oid, fid, name, okay);
                // don't fix for now so that we can debug this error
                //if (fix) setFID_(sidx, oid, newRandomFID_());

            } else if (!absent && fid == null) {
                error("present files must have non-null FID", sidx, oid, fid, name, okay);
                // don't fix for now so that we can debug this error
                //if (fix) setFID_(sidx, oid, newRandomFID_());
            }

        } else {
            assert type == OA.Type.ANCHOR;
            l.warn("anchors are not supported yet");
        }
    }
}
