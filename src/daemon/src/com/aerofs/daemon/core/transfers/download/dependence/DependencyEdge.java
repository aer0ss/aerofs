/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.transfers.download.dependence;

import com.aerofs.lib.graph.DirectedEdge;
import com.aerofs.lib.id.SOCID;

public class DependencyEdge extends DirectedEdge<SOCID>
{
    public enum DependencyType
    {
        UNSPECIFIED,
        // for dependencies we have not yet bothered to classify as they haven't been observed in
        // real-world deadlocks
        PARENT,
        // for when a received remote object depends on a parent folder or ancestor store object
        // that is not present locally
        NAME_CONFLICT,
        // for when a received remote object has the same name as local object; we request the
        // sender's knowledge of our local conflicting object
    }

    public DependencyEdge(SOCID src, SOCID dst)
    {
        super(src, dst);
    }

    /**
     * @return the DependencyType associated with this object
     * Subclasses should override this method; it is an approach to avoid instanceOf
     */
    public DependencyType type()
    {
        return DependencyType.UNSPECIFIED;
    }

    @Override
    public String toString()
    {
        return _src + "=" + type().ordinal() + "=>" + dst;
    }

}
