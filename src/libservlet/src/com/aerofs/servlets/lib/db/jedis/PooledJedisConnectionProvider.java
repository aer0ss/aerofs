/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db.jedis;

import com.aerofs.servlets.lib.db.IDatabaseConnectionProvider;
import org.apache.commons.pool.impl.GenericObjectPool;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPooledConnection;

import javax.annotation.Nullable;

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

    public void init_(String host, int port, @Nullable String password)
    {
        // Cannot call init more than once.
        assert _jedisPool == null;

        JedisPoolConfig config = new JedisPoolConfig();

        //TODO Move these settings to config file

        // There is a nice post about configuring jedis pool:
        // http://biasedbit.com/redis-jedispool-configuration
        // We don't need to hold a lot of idle connections - we want to dynamically adjust according
        // to load.
        // It is also good idea to test connections while idle so we can invalidate
        // stale connections before they are requested

        // Fail-fast behaviour, we don't like to keep the kids waiting
        config.setWhenExhaustedAction(GenericObjectPool.WHEN_EXHAUSTED_FAIL);
        // Tests whether connection is dead when connection retrieval method is called
        // So that failures do not reach the user as often.
        config.setTestOnBorrow(true);
        // About the same as the max number of tomcat threads.
        config.setMaxActive(64);
        // Number of connections to Redis that just sit there and do nothing
        config.setMaxIdle(32); // half of active
        // Minimum number of idle connections to Redis - these can be seen as always open
        // and ready to serve
        config.setMinIdle(4);
        // Tests whether connection is dead when returning a connection to the pool
        config.setTestOnReturn(true);
        // Tests whether connections are dead during idle periods
        config.setTestWhileIdle(true);
        // Maximum number of connections to test in each idle check
        config.setNumTestsPerEvictionRun(32);
        // Idle connection checking period
        config.setTimeBetweenEvictionRunsMillis(60000);
        // Maximum time, in milliseconds, to wait for a resource
        // when exausted action is set to WHEN_EXAUSTED_BLOCK
        config.setMaxWait(3000);

        // 2000 is the default timeout, there's no constructor that takes password without timeout
        _jedisPool = new JedisPool(config, host, port, 2000, password);
    }
}