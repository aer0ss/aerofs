/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple class to efficiently implement a lock-free, thread-safe listeners pattern.
 */
public class Listeners<T> implements Iterable<T>
{
    private List<T> _listeners = new CopyOnWriteArrayList<T>();

    /**
     * Adds a listener
     */
    public void add(T listener)
    {
        _listeners.add(listener);
    }

    /**
     * Removes a listener
     *
     * Note: if one or more threads are iterating over the listeners when this method is called,
     * then the removal will only be effective after all threads are done iterating. In other
     * words: your listener may still be called even after you call remove.
     */
    public void remove(T listener)
    {
        _listeners.remove(listener);
    }

    /**
     * Removes all listeners. The same warning applies as for remove()
     */
    public void clear()
    {
        _listeners.clear();
    }

    @Override
    public Iterator<T> iterator()
    {
        return _listeners.iterator();
    }

    /**
     * Convenience method to create a new instance of this class
     */
    public static <T> Listeners<T> create()
    {
        return new Listeners<T>();
    }
}
