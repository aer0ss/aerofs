/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * This class verifies the following invariant, for all SOIDs added
 * Invariant: at the end of a DirectoryService transaction, if the OA for an soid hasn't been
 * deleted, either
 * 1) the FID for the object must be null and there are no content attributes or
 *    it is expelled OR
 * 2) the FID is non-null and there exist content attributes (or the dir is not expelled)
 */
class PreCommitFIDConsistencyVerifier extends AbstractTransListener
{
    private final DirectoryService _ds;
    private final FrequentDefectSender _fds;

    private final Set<SOID> _soidsToVerify = Sets.newLinkedHashSet();

    PreCommitFIDConsistencyVerifier(DirectoryService ds, FrequentDefectSender fds)
    {
        _ds = ds;
        _fds = fds;
    }

    void verifyAtCommitTime(SOID soid)
    {
        _soidsToVerify.add(soid);
    }

    @Override
    public void committing_(Trans t)
            throws SQLException
    {
        assert _ds != null || _soidsToVerify.isEmpty() : _ds + " " + _soidsToVerify;
        if (_ds == null) return;

        List<OA> inconsistentObjects = Lists.newArrayListWithCapacity(_soidsToVerify.size());
        for (SOID soid : _soidsToVerify) {
            OA oa = _ds.getOANullable_(soid);
            if (oa != null && !oa.fidIsConsistentWithCAsOrExpulsion()) {
                inconsistentObjects.add(oa);
            }
        }

        if (!inconsistentObjects.isEmpty()) {
            SQLException e = new SQLException("inconsistent oas: " + inconsistentObjects);
            _fds.logSendAsync("ds fid inconsistent", e);
            throw e;
        }
    }
}
