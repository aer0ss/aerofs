package com.aerofs.lib.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * This class is for internal use only by database implementations.
 */
public abstract class AbstractDBIterator<E> implements IDBIterator<E>
{
    protected final ResultSet _rs;
    private boolean _closed;

    public AbstractDBIterator(ResultSet rs)
    {
        DBIteratorMonitor.addActiveIterator_(this);
        _rs = rs;
    }

    @Override
    public boolean next_() throws SQLException
    {
        return _rs.next();
    }

    @Override
    public void close_() throws SQLException
    {
        if (_closed) return;
        _closed = true;
        DBIteratorMonitor.removeActiveIterator_(this);
        _rs.close();
    }

    @Override
    public boolean closed_()
    {
        return _closed;
    }
}
