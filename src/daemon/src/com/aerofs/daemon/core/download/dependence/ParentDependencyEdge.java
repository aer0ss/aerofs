/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.download.dependence;

import com.aerofs.lib.id.SOCID;

public class ParentDependencyEdge extends DependencyEdge
{
    public ParentDependencyEdge(SOCID src, SOCID dst)
    {
        super(src, dst);
    }

    @Override
    public DependencyType type()
    {
        return DependencyType.PARENT;
    }

}
