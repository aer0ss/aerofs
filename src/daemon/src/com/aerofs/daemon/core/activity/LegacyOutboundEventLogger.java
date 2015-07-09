/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.activity;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;

public class LegacyOutboundEventLogger implements OutboundEventLogger
{
    private final static Logger l = Loggers.getLogger(LegacyOutboundEventLogger.class);

    private final DirectoryService _ds;
    private final IActivityLogDatabase _aldb;
    private final MapAlias2Target _a2t;
    private final TransManager _tm;

    @Inject
    public LegacyOutboundEventLogger(DirectoryService ds, IActivityLogDatabase aldb, TransManager tm,
                                     MapAlias2Target a2t)
    {
        _ds = ds;
        _a2t = a2t;
        _aldb = aldb;
        _tm = tm;
    }

    public void log_(int type, SOID soid, DID to) throws SQLException
    {
        Path path = _ds.resolveNullable_(soid);
        if (path == null) {
            // the SOID is obtained before the transfer start and the core lock may be released
            // during the transfer so by the time the transfer is finished the object may have
            // been aliased...
            SOID target = _a2t.dereferenceAliasedOID_(soid);
            if (!target.equals(soid)) path = _ds.resolveNullable_(target);
        }

        if (path == null) {
            l.warn("no path for outbound {} {} {}", soid, type, to);
            return;
        }

        l.debug("outbound {} {} {} {}", soid, type, path, to);
        try (Trans t = _tm.begin_()) {
            _aldb.insertActivity_(soid, type, path, null, ImmutableSet.of(to), t);
            t.commit_();
        }
    }

    public static class Noop implements OutboundEventLogger
    {
        @Override
        public void log_(int type, SOID soid, DID to) throws SQLException
        {

        }
    }
}
