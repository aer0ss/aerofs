package com.aerofs.daemon.core.protocol;

import java.util.Collection;

import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.lib.id.DID;

public interface IListRevHistoryListener
{
    /**
     * objects passed to this method are immutable
     */
    void received_(DID from, Collection<Revision> entries);
}
