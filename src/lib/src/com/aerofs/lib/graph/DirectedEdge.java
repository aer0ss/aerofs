/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.graph;

public class DirectedEdge<V extends Comparable<V>> implements Comparable<DirectedEdge<V>>
{
    public final V _src;
    public final V dst;

    public DirectedEdge(V src, V dst)
    {
        this._src = src;
        this.dst = dst;
    }

    @Override
    public String toString()
    {
        return _src + "=>" + dst;
    }

    @Override
    public int compareTo(DirectedEdge<V> e)
    {
        if (!_src.equals(e._src)) {
            return _src.compareTo(e._src);
        } else {
            return dst.compareTo(e.dst);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o)
    {
        DirectedEdge<V> e = (DirectedEdge<V>) o;
        return null == e || (_src.equals(e._src) && dst.equals(e.dst));
    }
}
