/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.srvlib.db;

import com.aerofs.lib.Util;
import org.apache.log4j.Logger;

import java.sql.SQLException;

import static com.aerofs.lib.Util.stackTrace2string;

public abstract class AbstractDatabaseTransaction<V>
{
    private static final Logger l = Util.l(AbstractDatabaseTransaction.class);

    private final AbstractDatabase _db;
    private boolean _completed = false;
    private boolean _commit = false; // prevent commits unless explicitly requested

    protected AbstractDatabaseTransaction(AbstractDatabase db)
    {
        this._db = db;
    }

    protected abstract V impl_(AbstractDatabase db, AbstractDatabaseTransaction<V> trans)
            throws Exception;

    public final void commit_()
    {
        assertNotCompleted();

        _commit = true;
    }

    public final void abort_()
    {
        assertNotCompleted();

        _commit = false;
    }

    public final V run_()
            throws Exception
    {
        assertNotCompleted();

        synchronized (_db) {
            try {
                _db.setAutoCommit(false);
                return impl_(_db, this);
            } catch (Exception e) {
                l.warn("transaction fail: caught:" + stackTrace2string(e));

                throw e;
            } finally {
                _completed = true;

                try {
                    if (_commit) {
                        _db.commit();
                    } else {
                        _db.rollback();
                    }
                } catch (SQLException e) {
                    l.warn("transaction cannot commit/rollback: caught:" + stackTrace2string(e));
                }

                try {
                    _db.setAutoCommit(true);
                } catch (SQLException e) {
                    assert false : ("cannot disable transactions: caught:" + stackTrace2string(e));
                }
            }
        }
    }

    private void assertNotCompleted()
    {
        assert !_completed : ("cannot run transaction twice");
    }
}
