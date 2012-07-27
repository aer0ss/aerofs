/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.dependence;

import com.aerofs.lib.graph.DirectedAcyclicGraph;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Directed Acyclic Graph (intended for dependencies of among downloads of SOCIDs)
 */
public class DownloadDependenciesGraph extends DirectedAcyclicGraph<SOCID, DependencyEdge>
{
    public static class ExDownloadDeadlock extends Exception
    {
        private static final long serialVersionUID = 0L;

        public final ImmutableList<DependencyEdge> _cycle;

        ExDownloadDeadlock(List<DependencyEdge> cycle)
        {
            this._cycle = ImmutableList.copyOf(cycle);
        }
    }

    /**
     * for testing only
     */
    void addEdge_(SOCID src, SOCID dst)
            throws Exception
    {
        DependencyEdge dd = new DependencyEdge(src, dst);
        addEdge_(dd);
    }

    @Override
    protected void cycleDetected_(List<DependencyEdge> cycle)
            throws Exception
    {
        throw new ExDownloadDeadlock(cycle);
    }

}
