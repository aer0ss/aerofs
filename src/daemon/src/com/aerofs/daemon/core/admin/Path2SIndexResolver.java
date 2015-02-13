/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.lib.db.UnlinkedRootDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExNotShared;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;

/**
 * This util class is used to get SIndex for a store. The SIndex is resolved after identifying if:
 * The store is linked or unlinked
 * The store is a root store or not.
 * The store is expelled or not.
 */
public class Path2SIndexResolver
{
    private static final Logger l = Loggers.getLogger(Path2SIndexResolver.class);

    private final IMapSID2SIndex _sid2sidx;
    private final TransManager _tm;
    private final DirectoryService _ds;
    private final UnlinkedRootDatabase _urdb;

    @Inject
    public Path2SIndexResolver(IMapSID2SIndex sid2sidx, TransManager tm, DirectoryService ds,
            UnlinkedRootDatabase urdb)
    {
        _sid2sidx = sid2sidx;
        _tm = tm;
        _ds = ds;
        _urdb = urdb;
    }

    SIndex getSIndex_(Path path) throws SQLException, ExExpelled, ExNotShared, ExNotFound
    {
        SIndex sidx;
        if (_urdb.getUnlinkedRoot(path.sid()) != null) {
            // Unlinked store.
            sidx = getSIndex_(path.sid());
        } else {
            // The store is linked or expelled.
            SOID soid = _ds.resolveThrows_(path);
            OA oa = _ds.getOA_(soid);
            // The path resolves to an anchor if its a non-root store or an expelled store.
            if (oa.isAnchor()) {
                sidx = getSIndex_(SID.anchorOID2storeSID(oa.soid().oid()));
            } else {
                // The path didn't resolve to an anchor, meaning its a root store.
                if (!soid.oid().isRoot()) throw new ExNotShared();
                sidx = soid.sidx();
            }
        }
        l.debug("SIndex for path: {} is {}", path , sidx);
        return sidx;
    }

    private SIndex getSIndex_(SID sid) throws SQLException, ExNotShared
    {
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) {
            try (Trans t = _tm.begin_()) {
                sidx = _sid2sidx.getAbsent_(sid, t);
                t.commit_();
            }
        }
        return sidx;
    }
}