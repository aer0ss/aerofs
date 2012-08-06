package com.aerofs.daemon.lib.db;

import java.sql.SQLException;

import com.aerofs.daemon.lib.LRUCache;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;

public class DBCache<K, V> extends LRUCache<K, V> implements ITransListener
{
    public DBCache(TransManager tm, boolean cacheNull, int capacity)
    {
        super(cacheNull, capacity);
        tm.addListener_(this);
    }

    public DBCache(TransManager tm, int capacity)
    {
        super(capacity);
        tm.addListener_(this);
    }

    @Override
    public void committing_(Trans t) throws SQLException
    {
    }

    @Override
    public void committed_()
    {
    }

    @Override
    public void aborted_()
    {
        // invalidate the entire cache to prevent modifications during the
        // transaction from being cached. a modification may be cached if
        // the client reads the database after a modification.
        //
        // TODO invalidate only what are cached during the transaction.
        //
        invalidateAll_();
    }
}
