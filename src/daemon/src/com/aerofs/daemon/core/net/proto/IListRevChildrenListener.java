package com.aerofs.daemon.core.net.proto;

import java.util.Collection;

import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.lib.id.DID;

public interface IListRevChildrenListener
{
    /**
     * objects passed to this method are immutable
     */
    void received_(DID from, Collection<Child> children);
}
