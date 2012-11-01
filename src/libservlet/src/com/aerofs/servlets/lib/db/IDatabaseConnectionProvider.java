/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

/**
 * Interface for creating database connections.
 */
public interface IDatabaseConnectionProvider<T>
{
    /**
     * Get a connection to the underlying database. This might be a pooled connection or it might
     * be a regular database connection. In either case, the connection should be closed when
     * the user is finished with it. When the object is pooled, this close operation will return
     * the object to the pool.
     */
    public T getConnection() throws ExDbInternal;
}