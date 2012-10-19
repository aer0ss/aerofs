package com.aerofs.daemon.lib.db.trans;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.ITransListener;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.commons.lang.ArrayUtils;

import javax.annotation.Nullable;

/**
 * Transactions. Client should not create Transaction objects directly. Use TransManager instead.
 */
public class Trans
{
    private boolean _commit;
    private boolean _ended;

    private final ArrayList<ITransListener> _listeners = Lists.newArrayListWithCapacity(4);

    // this field is accessed by {@link TransLocal} only
    Map<TransLocal<?>, Object> _transLocals;

    private final Factory _f;
    private final TransManager _tm;

    public static class Factory
    {
        private final IDBCW _dbcw;

        @Inject
        public Factory(CoreDBCW dbcw)
        {
            _dbcw = dbcw.get();
        }

        Trans create_(TransManager tm)
        {
            return new Trans(this, tm);
        }
    }

    private Trans(Factory fact, TransManager tm)
    {
        _f = fact;
        _tm = tm;
    }

    /**
     * the listeners are called in the reverse order of registration
     */
    public void addListener_(ITransListener l)
    {
        assert !_ended;
        _listeners.add(l);
    }

    /**
     * abort the transaction if commit_() hasn't been called after begin_().
     * otherwise commit the transaction
     */
    public void end_() throws SQLException
    {
        assert !_ended;

        if (_commit) {
            for (ITransListener l : _listeners) l.committing_(this);
            _tm.committing_(this);

            _f._dbcw.commit_();
        } else {
            _f._dbcw.abort_();
        }

        _ended = true;

        // call the listeners in the reverse order of registration
        if (_commit) {
            for (int i = _listeners.size() - 1; i >= 0; i--) _listeners.get(i).committed_();
            _tm.committed_();
        } else {
            for (int i = _listeners.size() - 1; i >= 0; i--) _listeners.get(i).aborted_();
            _tm.aborted_();
        }
    }

    /**
     * Concat the stack trace of {@code b} to that of {@code a}
     */
    private void concatStackTrace(Throwable a, Throwable b)
    {
        a.setStackTrace((StackTraceElement[])ArrayUtils.addAll(a.getStackTrace(),
                                                               b.getStackTrace()));
    }

    /**
     * abort the transaction if commit_() hasn't been called after begin_().
     * otherwise commit the transaction.
     *
     * If the rollback fails due to an exception, append to its the stack trace that of the
     * {@code rollbackCause}, if any, to avoid loosing valuable debugging information
     *
     * @param rollbackCause rollback cause, if any
     */
    public void end_(@Nullable Throwable rollbackCause) throws SQLException
    {
        try {
            end_();
        } catch (Error e) {
            if (rollbackCause != null) concatStackTrace(e, rollbackCause);
            throw e;
        } catch (RuntimeException e) {
            if (rollbackCause != null) concatStackTrace(e, rollbackCause);
            throw e;
        } catch (SQLException e) {
            if (rollbackCause != null) concatStackTrace(e, rollbackCause);
            throw e;
        }
    }

    public void commit_()
    {
        assert !_ended;
        assert !_commit;
        _commit = true;
    }

    public boolean ended_()
    {
        return _ended;
    }
}
