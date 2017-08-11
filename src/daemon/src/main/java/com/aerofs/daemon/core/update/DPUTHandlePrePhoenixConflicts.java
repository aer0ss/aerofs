package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase.RemoteContent;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.OID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * Pre-phoenix, conflicts used to propagate to all peers, with each file having a potentially
 * unbounded number of conflict branches whereas post-phoenix, conflicts remain purely local and
 * as a result there can be at most two branches: local (MASTER) and remote.
 *
 * This means that something must be done about old conflicts to avoid violating new invariants.
 *
 * Devices have no way to guess which, if any, of the local conflicts will end up being picked as
 * the winner by polaris so the safest thing to do is to drop all branches but MASTER and re-fetch
 * the actual winner if needed.
 *
 * Conflicts are moved to sync history to avoid any risk of data loss in weird corner cases.
 *
 *
 * Complication for Prominence Advisors
 * ------------------------------------
 *
 * These guys already went through the regular conversion (at least partially) and may thus have
 * a weird mix of pre and post phoenix conflicts. An attempt is made to distinguish this by looking
 * up the central version, if present, and comparing existing branches to available remote content
 * entries.
 */
public class DPUTHandlePrePhoenixConflicts extends PhoenixDPUT {
    private final static Logger l = Loggers.getLogger(DPUTHandlePrePhoenixConflicts.class);

    @Inject IDBCW _dbcw;
    @Inject TransManager _tm;
    @Inject DirectoryService _ds;
    @Inject IPhysicalStorage _ps;
    @Inject CentralVersionDatabase _cvdb;
    @Inject RemoteContentDatabase _rcdb;
    @Inject SIDMap _sm;
    @Inject StoreHierarchy _sh;
    @Inject ILinker _link;

    @Override
    public void runPhoenix() throws Exception {
        // TODO: incremental progress and split trans
        Set<SOID> ins = new HashSet<>();
        Set<SOKID> del = new HashSet<>();

        // scan all CAs
        try (Statement s = _dbcw.getConnection().createStatement()) {
            try (ResultSet rs = s.executeQuery("select c_s,c_o,c_k from c")) {
                while (rs.next()) {
                    KIndex kidx = new KIndex(rs.getInt(3));
                    SOKID k = new SOKID(new SIndex(rs.getInt(1)), new OID(rs.getBytes(2)), kidx);

                    Long v = _cvdb.getVersion_(k.sidx(), k.oid());

                    if (kidx.isMaster()) {
                        // specifically for Prominence Advisors
                        // conversion will normally do this
                        if (v == null) {
                            // master branch, no central version
                            // add dummy to avoid conflicts
                            ins.add(k.soid());
                        }
                    } else {
                        if (v != null) {
                            // post-phoenix: match against rcdb
                            ContentHash h = _ds.getCAHash_(k);
                            try (IDBIterator<RemoteContent> rcit = _rcdb.list_(k.sidx(), k.oid())) {
                                if (rcit.next_()) {
                                    RemoteContent rc = rcit.get_();
                                    if (v == rc.version && rc.hash.equals(h)) {
                                        l.info("match remote {} {}", k, v);
                                        continue;
                                    }
                                }
                            }
                        }
                        // pre-phoenix conflict branch: delete
                        del.add(k);
                    }
                }
            }
        }

        // need to populate the SIDMap so the _ds can resolve OAs and paths
        // FIXME (RD) instead of init'ing specific portions of the daemon, it'd be better if the deleting a conflict file line below didn't cause as many problems
        // ideally we could make extra functions that don't rely on init'ing SIDMaps everywhere, could also interact with the db directly
        for (SIndex sidx : _sh.getAll_()) { _sm.add_(sidx); }
        _link.populate();

        try (Trans t = _tm.begin_()) {
            for (SOKID k : del) {
                // skip objects in staging area
                ResolvedPath p = _ds.resolveNullable_(k.soid());
                if (p == null) continue;
                l.info("drop {}", k);
                _ds.deleteCA_(k.soid(), k.kidx(), t);
                _ps.newFile_(p, k.kidx()).delete_(PhysicalOp.APPLY, t);
            }
            t.commit_();
        }

        // depopulate it at the end for when the SIDMap is init'ed properly
        for (SIndex sidx : _sh.getAll_()) { _sm.delete_(sidx); }
        _link.clear();

        // add dummy central version:
        try (Trans t = _tm.begin_()) {
            for (SOID soid : ins) {
                _cvdb.setVersion_(soid.sidx(), soid.oid(), -1L, t);
            }
            t.commit_();
        }
    }
}
