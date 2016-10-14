/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import javax.annotation.Nullable;

import org.junit.Test;
import org.junit.Assert;
import redis.clients.jedis.JedisPooledConnection;

public class TestJedisThreadLocalTransaction extends AbstractJedisTest
{
    private @Nullable String getValue(String key)
            throws ExDbInternal
    {
        JedisPooledConnection jedis = getProvider().getConnection();
        String value = jedis.get(key);
        jedis.returnResource();

        return value;
    }

    @Test
    public void testSingleWriteInTransactionCompletesSuccessfully()
            throws ExDbInternal
    {
        // Write a simple key into the redis database.
        getTransaction().begin();
        getTransaction().get().set("foo", "bar");
        getTransaction().commit();
        getTransaction().cleanUp();

        // Verify the key has indeed been written.
        Assert.assertEquals("bar", getValue("foo"));
    }

    @Test
    public void testTransactionRollback()
            throws ExDbInternal
    {
        // Do a write, then change your mind and rollback. Expect the key not to be written.
        getTransaction().begin();
        getTransaction().get().set("foo", "bar");
        getTransaction().rollback();
        getTransaction().cleanUp();

        // Verify the key has not been written.
        Assert.assertEquals(null, getValue("foo"));
    }
}