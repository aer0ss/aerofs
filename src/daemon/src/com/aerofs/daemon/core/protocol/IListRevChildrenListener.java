package com.aerofs.daemon.core.protocol;

import java.util.Collection;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;

public interface IListRevChildrenListener
{
    /**
     * objects passed to this method are immutable
     */
    void received_(DID from, Collection<Child> children);
}
