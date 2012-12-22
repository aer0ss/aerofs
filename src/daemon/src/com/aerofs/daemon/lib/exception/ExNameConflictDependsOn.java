/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.exception;

import com.aerofs.daemon.core.protocol.dependence.DependencyEdge.DependencyType;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.base.id.DID;
import com.aerofs.lib.id.OCID;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBMeta;

import java.util.Set;

public class ExNameConflictDependsOn extends ExDependsOn
{
    private static final long serialVersionUID = 1L;

    public final OID _parent;
    public final Version _vRemote;
    public final PBMeta _meta;
    public final SOID _soidMsg;
    public final Set<OCID> _requested;

    public ExNameConflictDependsOn(OID oid, DID did, OID parent, Version vRemote, PBMeta meta,
            SOID soidMsg, Set<OCID> requested)
    {
        // Name conflicts only apply to META components
        super(new OCID(oid, CID.META), did, DependencyType.NAME_CONFLICT, true);
        _parent = parent;
        _vRemote = vRemote;
        _meta = meta;
        _soidMsg = soidMsg;
        _requested = requested;
    }
}
