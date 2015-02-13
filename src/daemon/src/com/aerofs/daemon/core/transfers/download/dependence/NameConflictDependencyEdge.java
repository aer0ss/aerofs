/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.transfers.download.dependence;

import com.aerofs.daemon.lib.exception.ExNameConflictDependsOn;
import com.aerofs.lib.Version;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBMeta;

public class NameConflictDependencyEdge extends DependencyEdge
{
    // Arguments required to call ReceiveAndApplyUpdate.resolveNameConflictByRenaming_
    final OID _parent;
    final Version _vSrc;
    final PBMeta _meta;
    final SOID _soidMsg;

    /**
     * Requires additional arguments to break the dependency in case of deadlock
     */
    NameConflictDependencyEdge(SOCID src, SOCID dst, OID parent, Version vSrc, PBMeta meta,
            SOID soidMsg)
    {
        super(src, dst);
        _parent = parent;
        _vSrc = vSrc;
        _meta = meta;
        _soidMsg = soidMsg;
    }

    public static NameConflictDependencyEdge fromException(SOCID src, SOCID dst,
            ExNameConflictDependsOn e)
    {
        return new NameConflictDependencyEdge(src, dst, e._parent, e._vRemote, e._meta,
                e._soidMsg);
    }

    @Override
    public DependencyType type()
    {
        return DependencyType.NAME_CONFLICT;
    }
}
