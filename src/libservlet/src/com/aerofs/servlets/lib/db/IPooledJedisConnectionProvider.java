/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import redis.clients.jedis.JedisPooledConnection;

/**
 * Interface required to facilitate unit testing.
 */
public interface IPooledJedisConnectionProvider
{
    public JedisPooledConnection getConnection();
}