/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.fs.IListLinkedAndExpelledSharedFolders;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.google.inject.Inject;

import java.sql.SQLException;

/**
 * Multiuser (TS) implementation of the IListLinkedAndExpelledSharedFolders. The TS should only have
 * linked/admitted folders as we do not allow selective syncing/expelling from the TS.
 */
public class MultiUserLinkedAndExpelledSharedFolders implements IListLinkedAndExpelledSharedFolders
{
    private final StoreHierarchy _ss;

    @Inject
    public MultiUserLinkedAndExpelledSharedFolders(StoreHierarchy ss)
    {
        _ss = ss;
    }

    @Override
    public PBSharedFolder getSharedFolder(SIndex sidx, SID sid) throws
            SQLException
    {
        return PBSharedFolder.newBuilder()
            .setName(_ss.getName_(sidx))
            .setPath(new Path(sid).toPB())
            .setAdmittedOrLinked(true)
            .setStoreId(sid.toPB())
            .build();
    }
}