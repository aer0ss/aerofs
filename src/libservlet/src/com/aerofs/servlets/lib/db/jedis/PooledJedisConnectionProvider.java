/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db.jedis;

import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPooledConnection;

public class PooledJedisConnectionProvider
        implements IDatabaseConnectionProvider<JedisPooledConnection>
{
    private JedisPool _jedisPool = null;

    // Because of commons pool v1.5.5 bug borrowing object from underlying GenericObjectPool
    // is not always thread safe: https://issues.apache.org/jira/browse/POOL-184
    // some jedis related issues:  https://github.com/xetorthio/jedis/issues/407
    // Making getConnection() synchronized until we upgrade commons-pool to later version
    // Currently we are using commons-pool v1.5.5 inside jedis-2.2.5.jar
    //
    @Override
    public synchronized JedisPooledConnection getConnection()
    {
        // Must call init first.
        assert _jedisPool != null;
        return _jedisPool.getResource();
    }

    public void init_(String host, int port)
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