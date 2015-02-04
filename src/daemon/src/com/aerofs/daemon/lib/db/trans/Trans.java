package com.aerofs.daemon.lib.db.trans;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.ITransListener;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkState;

/**
 * Transactions. Client should not create Transaction objects directly. Use TransManager instead.
 */
public class Trans implements AutoCloseable
{
    private boolean _commit;
    private boolean _ended;

    private final ArrayList<ITransListener> _listeners = Lists.newArrayListWithCapacity(4);

    // this field is accessed by {@link TransLocal} only
    Map<TransLocal<?>, Object> _transLocals;

    private final Factory _f;
    private final TransManager _tm;

    @Override
    public final void close() throws SQLException
    {
        end_();
    }

    public static class Factory
    {
        private final IDBCW _dbcw;

        @Inject
        public Factory(CoreDBCW dbcw)
        {
            this(dbcw.get());
        }

        public Factory(IDBCW dbcw)
        {
            _dbcw = dbcw;
        }

        Trans create_(TransManager tm)
        {
            return new Trans(this, tm);
        }
    }

    protected Trans(Factory fact, TransManager tm)
    {
        _f = fact;
        _tm = tm;
    }

    /**
     * The listeners are called in the reverse order of registration. The lifespan of the listener
     * is scoped in this transaction. To register listeners that live across all transactions,
     * use {@link TransManager#addListener_}.
     */
    public void addListener_(ITransListener l)
    {
        checkState(!_ended);
        _listeners.add(l);
    }

    /**
     * abort the transaction if commit_() hasn't been called after begin_().
     * otherwise commit the transaction
     */
    public void end_() throws SQLException
    {
        checkState(!_ended);

        // set _ended *before* calling methods that may throw, so that
        // TransManager#assertNoOngoingTransaction_() will not complain later if end_() throws.
        _ended = true;

        if (_commit) executeCommit_();
        else executeAbort_();
    }

    private void executeCommit_() throws SQLException
    {
        try {
            for (ITransListener l : _listeners) l.committing_(this);
            _tm.committing_(this);
            _f._dbcw.commit_();
        } catch (SQLException e) {
            executeAbort_();
            throw e;
        }

        // N.B. code after _dbcw.commit() must not throw

        for (int i = _listeners.size() - 1; i >= 0; i--) _listeners.get(i).committed_();
        _tm.committed_();
    }

    private void executeAbort_()
    {
        _f._dbcw.abort_();

        for (int i = _listeners.size() - 1; i >= 0; i--) _listeners.get(i).aborted_();
        _tm.aborted_();
    }

    public void commit_()
    {
        checkState(!_ended);
        checkState(!_commit);
        _commit = true;
    }

    public boolean ended_()
    {
        return _ended;
    }
}
