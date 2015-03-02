package com.aerofs.lib.db;

import java.sql.SQLException;

public class MockDBIterator<E> implements IDBIterator<E>
{
    private boolean _closed;
    protected int _cur;
    public final E[] elems;

    protected MockDBIterator(E[] elems)
    {
        _closed = false;
        _cur = -1;
        this.elems = elems;
    }

    @Override
    public E get_() throws SQLException
    {
        return elems[_cur];
    }

    @Override
    public boolean next_() throws SQLException
    {
        return (++_cur) < elems.length;
    }

    @Override
    public void close_() throws SQLException { _closed = true; }

    @Override
    public boolean closed_() { return _closed; }
}
