/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPooledConnection;

public class LocalJedisConnectionProvider implements IPooledJedisConnectionProvider
{
    private final JedisPool _jedisPool = new JedisPool(new JedisPoolConfig(), "localhost");

    public JedisPooledConnection getConnection()
    {
        return _jedisPool.getResource();
    }
}