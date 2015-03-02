package com.aerofs.fsck;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import com.aerofs.base.Loggers;
import com.google.inject.Inject;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

public class DBCheckOAAndCA
{
    private static final Logger l = Loggers.getLogger(DBCheckOAAndCA.class);

    // TODO close these statements after use
    private PreparedStatement _psListChildren;
    private PreparedStatement _psListCAttrs;

    private final IDBCW _dbcw;

    private static class Args {
        SOID _soid;
        String _name;
        OA.Type _type;
    }

    @Inject
    public DBCheckOAAndCA(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    public void check_(InOutArg<Boolean> okay) throws SQLException
    {
        // prepare statements
        _psListChildren = _dbcw.getConnection().prepareStatement(
                "select " + C_OA_OID + "," + C_OA_NAME + "," + C_OA_TYPE +
                " from " + T_OA +
                " where " + C_OA_SIDX + "=? and " + C_OA_PARENT + "=?" +
                " and " + C_OA_PARENT + " <> " + C_OA_OID);

        _psListCAttrs = _dbcw.getConnection().prepareStatement(
                "select count(*) from " + T_CA +
                " where " + C_CA_SIDX + "=? and " + C_CA_OID + "=?");

        ArrayList<SIndex> sidcs = new ArrayList<SIndex>();
        ResultSet rs = _dbcw.getConnection().createStatement().executeQuery(
                "select " + C_STORE_SIDX + " from " + T_STORE);
        while (rs.next()) sidcs.add(new SIndex(rs.getInt(1)));

        // list roots
        // must not call checkRecursive while iterating the result set
        // because that method also uses  _psListChildren.
        ArrayList<Args> roots = new ArrayList<Args>();
        for (SIndex sidx : sidcs) {
            roots.addAll(getChildren(new SOID(sidx, OID.ROOT)));
        }

        for (Args root : roots) checkRecursive_(root, okay);
    }

    private ArrayList<Args> getChildren(SOID soid) throws SQLException
    {
        _psListChildren.setInt(1, soid.sidx().getInt());
        _psListChildren.setBytes(2, soid.oid().getBytes());
        ResultSet rs = _psListChildren.executeQuery();

        ArrayList<Args> children = new ArrayList<Args>();
        while (rs.next()) {
            Args a = new Args();
            a._soid = new SOID(soid.sidx(), new OID(rs.getBytes(1)));
            a._name = rs.getString(2);
            a._type = OA.Type.values()[rs.getInt(3)];
            children.add(a);
        }

        return children;
    }

    private final Set<SOID> _soidSet = new LinkedHashSet<SOID>();

    private void checkRecursive_(Args a, InOutArg<Boolean> okay)
        throws SQLException
    {
        if (!_soidSet.add(a._soid)) {
            DBChecker.error("found loop", a._soid + ": " + _soidSet, okay);
            return;
        }
        try {
            _psListCAttrs.setInt(1, a._soid.sidx().getInt());
            _psListCAttrs.setBytes(2, a._soid.oid().getBytes());
            try (ResultSet rs = _psListCAttrs.executeQuery()) {
                if (!rs.next()) {
                    DBChecker.error("count(*) exists", "db", okay);

                } else {
                    int count = rs.getInt(1);
                    if (a._type != OA.Type.FILE) {
                        if (count != 0) {
                            String soid = a._soid.sidx() + "." + a._soid.oid().toStringFormal();
                            DBChecker.error("dir has no ca", soid + " " +
                                    a._name + " " + a._type + " has " + count, okay);
                        }

                        // currently don't support anchors
                        if (a._type == OA.Type.ANCHOR) {
                            l.warn("anchors are not supported yet");
                        }

                        for (Args child : getChildren(a._soid)) {
                            checkRecursive_(child, okay);
                        }
                    }
                }
            }
        } finally {
            _soidSet.remove(a._soid);
        }
    }

}
