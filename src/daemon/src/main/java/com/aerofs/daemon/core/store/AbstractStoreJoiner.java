/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Join/leave a store upon ACL changes, with multiplicity-specific behavior
 *
 * TODO: find a better name...
 *
 * See {@link com.aerofs.daemon.core.acl.ACLSynchronizer}
 */
public abstract class AbstractStoreJoiner implements IStoreJoiner
{
    protected final static Logger l = Loggers.getLogger(AbstractStoreJoiner.class);
    protected final ObjectCreator _oc;
    protected final ObjectSurgeon _os;
    protected final DirectoryService _ds;
    protected final ObjectDeleter _od;

    public AbstractStoreJoiner(DirectoryService ds, ObjectSurgeon os, ObjectCreator oc,
            ObjectDeleter od)
    {
        _ds = ds;
        _os = os;
        _oc = oc;
        _od = od;
    }

    /**
     * When a user loses access to a store, any locally present anchor is deleted.
     *
     * This method is meant to be used by subclasses and NOT meant to be reimplemented.
     */
    protected @Nullable Path deleteAnchorIfNeeded_(SIndex sidx, SID sid, Trans t) throws Exception
    {
        SOID soid = new SOID(sidx, SID.storeSID2anchorOID(sid));
        OA oa = _ds.getOANullable_(soid);
        if (oa == null) return null;

        checkState(oa.type() == Type.ANCHOR);

        final ResolvedPath path = _ds.resolve_(oa);
        if (path.isInTrash()) return null;
        l.info("deleting anchor {} {}", sidx, sid);
        _od.delete_(oa.soid(), PhysicalOp.APPLY, t);

        return path;
    }
}
