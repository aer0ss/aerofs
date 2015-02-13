/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.exception;

import com.aerofs.ids.OID;
import com.aerofs.daemon.core.transfers.download.dependence.DependencyEdge.DependencyType;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBMeta;

public class ExNameConflictDependsOn extends ExDependsOn
{
    private static final long serialVersionUID = 1L;

    public final OID _parent;
    public final Version _vRemote;
    public final PBMeta _meta;
    public final SOID _soidMsg;

    public ExNameConflictDependsOn(OID oid, OID parent, Version vRemote, PBMeta meta,
            SOID soidMsg)
    {
        // Name conflicts only apply to META components
        super(new OCID(oid, CID.META), DependencyType.NAME_CONFLICT);
        _parent = parent;
        _vRemote = vRemote;
        _meta = meta;
        _soidMsg = soidMsg;
    }
}
