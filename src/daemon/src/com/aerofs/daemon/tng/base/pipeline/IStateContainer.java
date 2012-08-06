/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pipeline;

import com.google.common.util.concurrent.ListenableFuture;

public interface IStateContainer
{
    /**
     * Adds some object representing state to the IStateContainer. This is used by a handler from
     * the pipeline for storing stateful information across IConnections, for the lifetime of the
     * IStateContainer.
     *
     * @param key The key to which to map the state to
     * @param state The stateful information to store
     * @return A future that is set with the state when it is removed from the container
     */
    ListenableFuture<Object> addState_(String key, Object state);

    /**
     * Removes the state associated with the given key. Removing a state object triggers its
     * associated future
     *
     * @param key The key that points to the object to remove
     * @return The state that was removed
     */
    Object removeState_(String key);

    /**
     * Retrieves the state associated with the given key
     *
     * @param key The key that points to the object to retrieve
     * @return The retrieved state
     */
    Object getState_(String key);
}
