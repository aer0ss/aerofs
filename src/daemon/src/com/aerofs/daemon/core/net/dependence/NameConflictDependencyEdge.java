/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.dependence;

import com.aerofs.lib.Version;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBMeta;

import java.util.Set;

public class NameConflictDependencyEdge extends DependencyEdge
{
    // Arguments required to call ReceiveAndApplyUpdate.resolveNameConflictByRenaming_
    final DID _did;
    final OID _parent;
    final Version _vSrc;
    final PBMeta _meta;
    final SOID _soidMsg;
    final Set<OCID> _requested;

    /**
     * Requires additional arguments to break the dependency in case of deadlock
     */
    public NameConflictDependencyEdge(SOCID src, SOCID dst, DID did, OID parent, Version vSrc,
            PBMeta meta, SOID soidMsg, Set<OCID> requested)
    {
        super(src, dst);
        _did = did;
        _parent = parent;
        _vSrc = vSrc;
        _meta = meta;
        _soidMsg = soidMsg;
        _requested = requested;
    }

    @Override
    public DependencyType type()
    {
        return DependencyType.NAME_CONFLICT;
    }
}
