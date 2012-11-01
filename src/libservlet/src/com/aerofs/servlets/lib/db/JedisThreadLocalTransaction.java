/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import redis.clients.jedis.JedisPooledConnection;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Class that facilitates thread local Jedis (Java Redis) transactions.
 */
public class JedisThreadLocalTransaction
    extends AbstractThreadLocalTransaction<JedisException>
    implements IThreadLocalTransaction<JedisException>
{
    private IPooledJedisConnectionProvider _provider;

    // The Jedis API requires that we hold on to the transaction object as well. Encapsulate all
    // the objects we need to hold in the jedis holder object.
    private ThreadLocal<JedisThreadLocalObjectHolder> _jedisHolder = new ThreadLocal<JedisThreadLocalObjectHolder>();

    public JedisThreadLocalTransaction(IPooledJedisConnectionProvider provider)
    {
        _provider = provider;
    }

    @Override
    protected boolean isInTransaction()
    {
        return _jedisHolder.get() != null;
    }

    private void closeConnection()
    {
        if (isInTransaction()) {
            _jedisHolder.get().getConnection().returnResource();
            _jedisHolder.remove();
        }
    }

    public Transaction get()
            throws ExDbInternal
    {
        assert isInTransaction();
        return _jedisHolder.get().getTransaction();
    }

    @Override
    public void begin()
    {
        assert !isInTransaction();

        JedisPooledConnection jedis = _provider.getConnection();
        Transaction transaction = jedis.multi();

        _jedisHolder.set(new JedisThreadLocalObjectHolder(jedis, transaction));
    }

    @Override
    public void commit()
    {
        assert isInTransaction();

        // Execute the redis commands issues after multi (called in the begin function of this
        // class) and return the resource to the redis pool.
        _jedisHolder.get().getTransaction().exec();
        closeConnection();
    }

    @Override
    public void handleException()
    {
        handleExceptionHelper();
    }

    @Override
    public void rollback()
    {
        assert isInTransaction();

        // Discard all redis commands issued after the multi call in the begin function of this
        // class.
        _jedisHolder.get().getTransaction().discard();
        closeConnection();
    }

    @Override
    public void cleanUp()
    {
        if (isInTransaction()) {
            rollback();
        } else {
            closeConnection();
        }
    }

    /**
     * Call this function if the resource is broken in any way. One example use case is if clean up
     * throws a jedis connection exception.
     */
    public void cleanUpBrokenResource() {
        if (isInTransaction()) {
            _jedisHolder.get().getConnection().returnBrokenResource();
            _jedisHolder.remove();
        }
    }

    private static class JedisThreadLocalObjectHolder
    {
        private final JedisPooledConnection _connection;
        private final Transaction _transaction;

        JedisThreadLocalObjectHolder(JedisPooledConnection connection, Transaction transaction)
        {
            this._connection = connection;
            this._transaction = transaction;
        }

        public JedisPooledConnection getConnection()
        {
            return _connection;
        }

        public Transaction getTransaction()
        {
            return _transaction;
        }
    }
}
