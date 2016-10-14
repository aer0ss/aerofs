/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db.jedis;

import com.aerofs.servlets.lib.db.ExDbInternal;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

public abstract class AbstractJedisDatabase
{
    private JedisThreadLocalTransaction _transaction;

    public AbstractJedisDatabase(JedisThreadLocalTransaction transaction)
    {
        _transaction = transaction;
    }

    protected final Transaction getTransaction()
            throws JedisException
    {
        Transaction transaction;

        try {
            transaction = _transaction.get();
        } catch (ExDbInternal e) {
            throw new JedisException(e);
        }

        return transaction;
    }
}
