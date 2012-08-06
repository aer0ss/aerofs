/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IStateContainer;
import com.aerofs.lib.async.UncancellableFuture;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class PeerStateContainer implements IStateContainer
{
    private static class Entry
    {
        public Object state;
        public UncancellableFuture<Object> removalFuture;

        public Entry(Object state, UncancellableFuture<Object> removalFuture)
        {
            this.state = state;
            this.removalFuture = removalFuture;
        }
    }

    private final Map<String, Entry> _stateMap = new HashMap<String, Entry>();

    @Override
    public ListenableFuture<Object> addState_(String key, Object state)
    {
        if (_stateMap.containsKey(key)) {
            throw new IllegalArgumentException("Key already set: " + key);
        }

        UncancellableFuture<Object> removalFuture = UncancellableFuture.create();
        _stateMap.put(key, new Entry(state, removalFuture));

        return removalFuture;
    }

    @Nullable
    @Override
    public Object removeState_(String key)
    {
        Entry entry = _stateMap.remove(key);
        if (entry != null) {
            entry.removalFuture.set(entry.state);
            return entry.state;
        }
        return null;
    }

    @Nullable
    @Override
    public Object getState_(String key)
    {
        Entry entry = _stateMap.get(key);
        if (entry != null) {
            return entry.state;
        }
        return null;
    }

    /**
     * Destroys this container, iterating over all state objects and triggering their associated
     * futures
     */
    public void destroy_()
    {
        for (Entry entry : _stateMap.values()) {
            entry.removalFuture.set(entry.state);
        }
        _stateMap.clear();
    }
}
