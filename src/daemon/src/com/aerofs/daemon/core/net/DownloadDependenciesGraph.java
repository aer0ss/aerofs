/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import java.util.Map.Entry;
import java.util.Queue;

import com.aerofs.lib.Util;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.apache.log4j.Logger;

/**
 * Directed Acyclic Graph (intended for dependencies of among downloads of SOCKIDs)
 */
public class DownloadDependenciesGraph<T extends Comparable<T>>
{
    private static final Logger l = Util.l(DownloadDependenciesGraph.class);
    // The key "depends on" the value
    private final Multimap<T, T> _mmap = TreeMultimap.create();

    /**
     * @param src depends on {@code dst}
     */
    public void addEdge_(T src, T dst)
    {
        if (pathExists_(dst, src)) {
            deadlockDetected_(src, dst);
        }

        // We currently don't expect to add the same dependency twice,
        // but maybe that's subject to change
        Util.verify(_mmap.put(src, dst));
    }

    public void removeEdge_(T src, T dst)
    {
        Util.verify(_mmap.remove(src, dst));
    }

    public void removeOutwardEdges_(T src)
    {
        Util.verify(_mmap.removeAll(src));
    }

    /**
     * @return true iff a path exists between src and dst in the graph
     * TODO (MJ) this might be really inefficient relative to a smarter Bellman-Ford-like
     * algorithm?
     */
    public boolean pathExists_(T src, T dst)
    {
        Queue<T> nodesToCheck = Lists.newLinkedList();
        nodesToCheck.add(src);

        // Perform a breadth-first traversal of the graph, searching for a path
        while (!nodesToCheck.isEmpty()) {
            T cur = nodesToCheck.poll();

            if (cur.equals(dst)) {
                l.info("path " + src + "->...->" + dst + " exists");
                return true;
            }

            // Add all the neighbours of cur to the queue to check
            nodesToCheck.addAll(_mmap.get(cur));
        }
        return false;
    }

    public boolean edgeExists_(T src, T dst)
    {
        return _mmap.get(src).contains(dst);
    }

    private void deadlockDetected_(T dependent, T k)
    {
        String msg = "sync dl deadlock: existing ";
        for (Entry<T, T> en : _mmap.entries()) {
            msg += en.getKey() + " => " + en.getValue() + ", ";
        }
        msg += "adding " + dependent + " => " + k;
        Util.fatal(msg);
    }
}
