package com.aerofs.servlets.lib.db;

import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.testlib.AbstractBaseTest;
import org.junit.Before;
import redis.clients.jedis.JedisPooledConnection;

public class AbstractJedisTest extends AbstractBaseTest
{
    private LocalJedisConnectionProvider _provider = new LocalJedisConnectionProvider();
    private JedisThreadLocalTransaction _transaction = new JedisThreadLocalTransaction(_provider);

    protected void flushAll() throws ExDbInternal
    {
        JedisPooledConnection jedis = _provider.getConnection();
        jedis.flushAll();
        jedis.returnResource();
    }

    protected JedisThreadLocalTransaction getTransaction()
    {
        return _transaction;
    }

    protected LocalJedisConnectionProvider getProvider()
    {
        return _provider;
    }

    @Before
    public void setupAbstractJedisTest()
            throws ExDbInternal
    {
        // Flush everything in the redis database before we start testing.
        flushAll();
    }
}