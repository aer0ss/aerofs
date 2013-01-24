/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import javax.annotation.Nullable;

import com.aerofs.testlib.AbstractTest;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import junit.framework.Assert;
import redis.clients.jedis.JedisPooledConnection;

public class TestJedisThreadLocalTransaction extends AbstractTest
{
    private LocalJedisConnectionProvider _provider = new LocalJedisConnectionProvider();
    private JedisThreadLocalTransaction _transaction = new JedisThreadLocalTransaction(_provider);

    private void flushAll()
            throws ExDbInternal
    {
        JedisPooledConnection jedis = _provider.getConnection();
        jedis.flushAll();
        jedis.returnResource();
    }

    private @Nullable String getValue(String key)
            throws ExDbInternal
    {
        JedisPooledConnection jedis = _provider.getConnection();
        String value = jedis.get(key);
        jedis.returnResource();

        return value;
    }

    @Before
    public void setupTestJedisThreadLocalTransaction()
            throws ExDbInternal
    {
        // Flush everything in the redis database before we start testing.
        flushAll();
    }

    @After
    public void teardownTestJedisThreadLocalTransaction()
            throws ExDbInternal
    {
        flushAll();
    }

    @Test
    public void testSingleWriteInTransactionCompletesSuccessfully()
            throws ExDbInternal
    {
        // Write a simple key into the redis database.
        _transaction.begin();
        _transaction.get().set("foo", "bar");
        _transaction.commit();
        _transaction.cleanUp();

        // Verify the key has indeed been written.
        Assert.assertEquals("bar", getValue("foo"));
    }

    @Test
    public void testTransactionRollback()
            throws ExDbInternal
    {
        // Do a write, then change your mind and rollback. Expect the key not to be written.
        _transaction.begin();
        _transaction.get().set("foo", "bar");
        _transaction.rollback();
        _transaction.cleanUp();

        // Verify the key has not been written.
        Assert.assertEquals(null, getValue("foo"));
    }
}