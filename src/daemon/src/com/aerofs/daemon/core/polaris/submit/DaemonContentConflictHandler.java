package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase.ContentChange;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.SortedMap;

/**
 * WATCH OUT FOR THIS ONE WEIRD TRICK:
 * ----------------------------------
 *
 * Upon restoration of an appliance from an outdated backup, information about changes
 * accepted by polaris will have been lost. In such a case recovery is achieved by
 * unlinking and reinstalling all desktop clients (eventually it'd be nice to improve
 * or at least somehow automate that recovery step but that's a separate matter).
 *
 * The result of this is that all clients are trying to submit the initial content version
 * for every object. For objects that have not been changed since the backup, things will
 * resolve smoothly, however for other objects the new hash will not match any of the
 * accepted content versions and because clients have lost version information in the
 * unlink their content submissions will be rejected.
 *
 * If at least one client remains which can serve an outdated version of the content
 * known to polaris then all clients will eventually receive it, show a conflict to
 * the user and users will be able to resolve the conflict in favor of the newer version.
 *
 * However, if no such outdated version can be synced, no clients would ever create a
 * conflict branch and the end-user would remain unaware of the issue or perceive it
 * as a unexpected no-sync and have no way to resolve it.
 *
 * To work around this, the client will, upon reception of a VERSION_CONFLICT when trying
 * to submit a local change, create a dummy conflict branch to account for the presence
 * of unavailable remote content such that the user will be notified of it and have
 * the opportunity to resolve the issue.
 */
public class DaemonContentConflictHandler extends ContentSubmitConflictHandler {
    private final TransManager _tm;
    private final DirectoryService _ds;
    private final CentralVersionDatabase _cvdb;
    private final RemoteContentDatabase _rcdb;

    @Inject
    public DaemonContentConflictHandler(TransManager tm, DirectoryService ds,
                                        CentralVersionDatabase cvdb, RemoteContentDatabase rcdb) {
        _tm = tm;
        _ds = ds;
        _cvdb = cvdb;
        _rcdb = rcdb;
    }

    public boolean onConflict_(ContentChange c, LocalChange lc, String body)
            throws SQLException {
        l.info("conflict {}{}: {}", c.sidx, c.oid, lc.localVersion, lc.hash);

        OA oa = _ds.getOANullable_(new SOID(c.sidx, c.oid));
        if (oa == null) {
            l.info("object disappeared");
            return false;
        }

        SortedMap<KIndex, CA> cas = oa.cas();
        if (cas.size() > 1) {
            l.debug("conflict appeared locally");
            return false;
        }

        Long cv = _cvdb.getVersion_(c.sidx, c.oid);
        Long mv = _rcdb.getMaxVersion_(c.sidx, c.oid);

        if (cv == null && mv != null) {
            l.info("creating empty conflict branch");
            try (Trans t = _tm.begin_()) {
                KIndex kidx = KIndex.MASTER.increment();
                // NB: we do NOT set the central version as this branch does not correspond
                // to any accepted remote content
                _ds.createCA_(oa.soid(), kidx, t);
                _ds.setCAHash_(new SOKID(oa.soid(), kidx), ContentHash.EMPTY, t);
                // NB: we do NOT create a physical file
                //  - no codepath should ever attempt to access the content of a dummy conflict
                //  - the only physical op with dummy conflict should be deletion and all physical
                //    storage can cope with deletion of a non-existent file
                //  - failing fast in case a codepath incorrectly tries to touch a dummy conflict
                //    will bring bugs to light faster
                //  - avoid wasting creating useless entries in sync history when the conflict is
                //    resolved
                t.commit_();
            }
        }

        return true;
    }
}
