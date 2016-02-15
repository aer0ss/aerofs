/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.graph;

import com.aerofs.base.Loggers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public abstract class DirectedAcyclicGraph<V extends Comparable<V>, E extends DirectedEdge<V>>
{
    private static final Logger l = Loggers.getLogger(DirectedAcyclicGraph.class);

    protected final Multimap<V, E> _mmap = TreeMultimap.create();

    /**
     * Add edge to the graph
     * TODO (MJ) if an EdgeFactory is created, we can write the user-friendlier addEdge(V v1, V v2)
     */
    public void addEdge_(E edge)
        throws Exception
    {
        // N.B. this is O(E) right now, making construction of a graph O(E^2). There must be
        // an algorithm that maintains state about paths as the graph is built, permitting
        // cycle-detection in O(1) at each edge insertion.  TODO (MJ) make more efficient
        List<E> path = pathBetween_(edge.dst, edge._src);

        if (!path.isEmpty()) {
            path.add(edge);
            cycleDetected_(path);
        }

        // We currently don't expect to add the same edge twice.
        final boolean changed = _mmap.put(edge._src, edge);
        assert changed : "Already exists " + edge;
    }

    public void removeEdge_(E e)
    {
        boolean changed = _mmap.remove(e._src, e);
        if (!changed) l.warn("not in graph: " + e);
    }

    public void removeOutwardEdges_(V v)
    {
        _mmap.removeAll(v);
    }

    /**
     * @return a list of edges that represent the path from _src to dst, or an empty list if
     * there is no such path
     */
    public List<E> pathBetween_(V src, V dst)
    {
        Map<V, E> predecessors = Maps.newTreeMap();

        // Breadth-first search, looking for a path
        // N.B. a union find or two-directional DFS might be faster
        // http://www.cs.princeton.edu/~rs/talks/PathsInGraphs07.pdf
        Queue<E> edgesToVisit = Lists.newLinkedList();
        edgesToVisit.addAll(_mmap.get(src));
        while (!edgesToVisit.isEmpty()) {
            E e = edgesToVisit.poll();
            predecessors.put(e.dst, e);
            if (e.dst.equals(dst)) {
                final List<E> path = predecessorsMapToPath_(predecessors, src, dst);
                l.debug(src + "-...->" + dst + " path exists: " + path);
                return path;
            }

            edgesToVisit.addAll(_mmap.get(e.dst));
        }

        return Lists.newLinkedList();
    }

    private List<E> predecessorsMapToPath_(Map<V, E> map, V src, V dst)
    {
        LinkedList<E> path = Lists.newLinkedList();

        V cur = dst;
        do {
            E predEdge = map.get(cur);
            assert predEdge.dst.equals(cur);
            path.addFirst(map.get(cur));
            cur = predEdge._src;
        } while (!cur.equals(src));

        return path;
    }

    public boolean edgeExists_(V src, V dst)
    {
        for (E e : _mmap.get(src)) {
            if (e.dst.equals(dst)) return true;
        }
        return false;
    }

    public String dumpContents_()
    {
        String contents = "";
        for (E edge : _mmap.values()) contents += edge + ", ";
        return contents;
    }

    /**
     * Subclasses must define how to handle that a cycle has been created between _src and dst
     */
    abstract protected void cycleDetected_(List<E> cycle) throws Exception;
}
