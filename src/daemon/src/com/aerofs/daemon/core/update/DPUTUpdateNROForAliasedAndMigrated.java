/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.update.DPUTUtil.IDatabaseOperation;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.labeling.L;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import static com.aerofs.daemon.lib.db.CoreSchema.*;
import static com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema.*;

/**
 * A bug in LinkedStorage caused the NRODatabase to become stale when NROs where migrated
 * or aliased.
 *
 * This was not immediately apparent but later caused crashes in Ritual calls.
 */
public class DPUTUpdateNROForAliasedAndMigrated implements IDaemonPostUpdateTask
{
    private final static Logger l = Loggers.getLogger(DPUTUpdateNROForAliasedAndMigrated.class);

    private final IDBCW _dbcw;
    private final RockLog _rocklog;

    public DPUTUpdateNROForAliasedAndMigrated(CoreDBCW dbcw, RockLog rocklog)
    {
        _dbcw = dbcw.get();
        _rocklog = rocklog;
    }

    @Override
    public void run() throws Exception
    {
        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, new IDatabaseOperation() {
            @Override
            public void run_(Statement s) throws SQLException
            {
                ResultSet rs = s.executeQuery(DBUtil.select(T_NRO,
                        C_NRO_SIDX, C_NRO_OID, C_NRO_CONFLICT_OID));

                Set<SOID> nros = Sets.newHashSet();
                Set<SOID> conflicts = Sets.newHashSet();

                try {
                    while (rs.next()) {
                        SIndex sidx = new SIndex(rs.getInt(1));
                        OID nro = new OID(rs.getBytes(2));
                        nros.add(new SOID(sidx, nro));
                        byte[] conflict = rs.getBytes(3);
                        if (conflict != null) conflicts.add(new SOID(sidx, new OID(conflict)));
                    }
                } finally {
                    rs.close();
                }

                int updatedNRO = updateOIDs(s, C_NRO_OID, nros);
                int updatedConflict = updateOIDs(s, C_NRO_CONFLICT_OID, conflicts);

                // migration invariants do not hold on TS
                // I'm reasonably sure the SIndex update would be a noop anyway
                int updatedSIndex = L.isMultiuser() ? -1 : updateSIndex(s, nros);

                if (!nros.isEmpty()) {
                    l.info("{} {}", nros.size(), conflicts.size());
                    _rocklog.newDefect("dput.aliasnro")
                            .addData("nro", nros.size())
                            .addData("conflicts", conflicts.size())
                            .addData("updated-nro", updatedNRO)
                            .addData("updated-conflicts", updatedConflict)
                            .addData("updated-sidx", updatedSIndex)
                            .send();
                }
            }
        });
    }

    private int updateOIDs(Statement s, String column, Set<SOID> nros) throws SQLException
    {
        int n = 0;
        for (SOID soid : nros) {
            // object found in NRO db is an alias: substitute with target
            byte[] target = target(s, soid);
            if (target != null) {
                updateOID(s, column, soid, target);
                ++n;
            }
        }
        return n;
    }

    private @Nullable byte[] target(Statement s, SOID soid) throws SQLException
    {
        PreparedStatement ps = s.getConnection().prepareStatement(DBUtil.selectWhere(T_ALIAS,
                C_ALIAS_SIDX + "=? and " + C_ALIAS_SOURCE_OID + "=?",
                C_ALIAS_TARGET_OID));
        ps.setInt(1, soid.sidx().getInt());
        ps.setBytes(2, soid.oid().getBytes());
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getBytes(1) : null;
    }

    private void updateOID(Statement s, String column, SOID soid, byte[] newOID)
            throws SQLException
    {
        l.info("update oid {} {}", soid, BaseUtil.hexEncode(newOID));
        PreparedStatement ps = s.getConnection().prepareStatement(DBUtil.updateWhere(T_NRO,
                C_NRO_SIDX + "=? and " + column + "=?", column));
        ps.setBytes(1, newOID);
        ps.setInt(2, soid.sidx().getInt());
        ps.setBytes(3, soid.oid().getBytes());
        ps.executeUpdate();
    }

    private int updateSIndex(Statement s, Set<SOID> nros) throws SQLException
    {
        int n = 0;
        Set<SIndex> stores = stores(s);
        for (SOID soid : nros) {
            int sidx = admitted(s, stores, soid);
            if (sidx == -1) {
                // not admitted in any store:
                // hopefully the botched migration will be recovered after the update
                // if not, unlink/reinstall will be required
                l.warn("botched migration {}", soid);
                _rocklog.newDefect("dput.nro.migration.botched")
                        .addData("soid", soid.toString())
                        .send();
            } else if (sidx != soid.sidx().getInt()) {
                // admitted in different store: update SIndex
                updateSIndex(s, soid, sidx);
                ++n;
            }
        }
        return n;
    }

    private Set<SIndex> stores(Statement s) throws SQLException
    {
        ResultSet rs = s.executeQuery(DBUtil.select(T_STORE, C_STORE_SIDX));
        try {
            Set<SIndex> r = Sets.newHashSet();
            while (rs.next()) {
                r.add(new SIndex(rs.getInt(1)));
            }
            return r;
        } finally {
            rs.close();
        }
    }

    private int admitted(Statement s, Set<SIndex> stores, SOID soid) throws SQLException
    {
        PreparedStatement ps = s.getConnection().prepareStatement(DBUtil.selectWhere(T_OA,
                C_OA_SIDX + "=? and " + C_OA_OID + "=? and " + C_OA_FLAGS + "=0",
                "count(*)"));
        for (SIndex sidx : stores) {
            ps.setInt(1, sidx.getInt());
            ps.setBytes(2, soid.oid().getBytes());
            ResultSet rs = ps.executeQuery();
            try {
                if (DBUtil.binaryCount(rs)) return sidx.getInt();
            } finally {
                rs.close();
            }
        }
        return -1;
    }

    private void removeNRO(Statement s, SOID soid) throws SQLException
    {
        l.info("remove {}", soid);
        PreparedStatement ps = s.getConnection().prepareStatement(DBUtil.deleteWhere(T_NRO,
                C_NRO_SIDX + "=? and " + C_NRO_OID + "=?"));
        ps.setInt(1, soid.sidx().getInt());
        ps.setBytes(2, soid.oid().getBytes());
        ps.executeUpdate();
    }

    private void updateSIndex(Statement s, SOID soid, int newSIndex)
            throws SQLException
    {
        l.info("update sidx {} {}", soid, newSIndex);
        PreparedStatement ps = s.getConnection().prepareStatement(DBUtil.updateWhere(T_NRO,
                C_NRO_SIDX + "=? and " + C_NRO_OID + "=?",
                C_NRO_SIDX));
        ps.setInt(1, newSIndex);
        ps.setInt(2, soid.sidx().getInt());
        ps.setBytes(3, soid.oid().getBytes());
        ps.executeUpdate();
    }
}
