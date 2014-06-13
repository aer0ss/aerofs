/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.fs.IListLinkedAndExpelledSharedFolders;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.SQLException;

/**
 * Singleuser (TS) implementation of the IListLinkedAndExpelledSharedFolders. This will return all
 * linked and expelled folders. Unlinked folders are already checked for in HdListSharedFolders.
 */
public class SingleUserLinkedAndAdmittedSharedFolders implements IListLinkedAndExpelledSharedFolders
{
    private final IStores _ss;
    private final DirectoryService _ds;
    private final IMapSID2SIndex _sid2sidx;
    private final IMapSIndex2SID _sidx2sid;
    private final CfgRootSID _cfgRootSid;

    @Inject
    public SingleUserLinkedAndAdmittedSharedFolders(IStores ss, DirectoryService ds,
            IMapSID2SIndex sid2sidx, IMapSIndex2SID sidx2sid, CfgRootSID cfgRootSid)
    {
        _ss = ss;
        _ds = ds;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
        _cfgRootSid = cfgRootSid;
    }

    @Override
    public @Nullable PBSharedFolder getSharedFolder(SIndex sidx, SID sid)
            throws SQLException
    {
        // Check if the store is a root. If it is a root we can use the IStores interface to return
        // the name and compute the path by resolving the root object of the store since root stores
        // don't have an anchor.
        if (_sidx2sid.getNullable_(sidx) != null && _ss.isRoot_(sidx)) {
            return PBSharedFolder.newBuilder()
                    .setName(_ss.getName_(sidx))
                    .setPath(new Path(sid).toPB())
                    .setAdmittedOrLinked(true)
                    .setStoreId(sid.toPB())
                    .build();
        } else {
            // In the case that the store isn't a root, use OA of the anchor to the store to compute
            // the name and path of the store.
            SIndex sidxRoot = _sid2sidx.get_(_cfgRootSid.get());
            OA oa = _ds.getOANullable_(new SOID(sidxRoot, SID.storeSID2anchorOID(sid)));
            // We check for oa to be null mainly because of race conditions. For example: consider a
            // user with two devices A and B. If he creates a folder foo in A (and this also
            // propagates to B) and then decides to share foo from A and for some reason this
            // doesn't propagate to B. In B the store will still be accessible but the OA would be
            // null.
            if (oa == null) {
                return null;
            }
            return PBSharedFolder.newBuilder()
                .setName(oa.name())
                .setPath(_ds.resolve_(oa).toPB())
                .setAdmittedOrLinked(!oa.isExpelled())
                .setStoreId(sid.toPB())
                .build();
        }
    }
}