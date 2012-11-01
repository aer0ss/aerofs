/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPooledConnection;

public class PooledJedisConnectionProvider implements IPooledJedisConnectionProvider
{
    private JedisPool _jedisPool = null;

    public JedisPooledConnection getConnection()
    {
        // Must call init first.
        assert _jedisPool != null;
        return _jedisPool.getResource();
    }

    public void init_(String host, short port)
    {
        // Cannot call init more than once.
        assert _jedisPool == null;

        JedisPoolConfig config = new JedisPoolConfig();

        // So that failures do not reach the user as often.
        config.setTestOnBorrow(true);
        // About the same as the max number of tomcat threads.
        config.setMaxActive(64);

        _jedisPool = new JedisPool(config, host, port);
    }
}