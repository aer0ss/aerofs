package com.aerofs.daemon.lib.db.trans;

import java.sql.SQLException;
import java.util.ArrayList;

import javax.inject.Inject;

import com.aerofs.daemon.lib.db.ITransListener;
import com.aerofs.lib.Util;
import com.google.common.collect.Lists;

/* usage:
 *
 *  Trans trans = tm.begin_();
 *  try {
 *      ...
 *      trans.commit_();
 *  } finally {
 *      trans.end_();
 *  }
 */
public class TransManager
{
    private final Trans.Factory _factTrans;
    private final ArrayList<ITransListener> _listeners = Lists.newArrayList();

    private Trans _ongoing; // for debugging
    private Thread _ongoingThread; // for debugging

    @Inject
    public TransManager(Trans.Factory factTrans)
    {
        _factTrans = factTrans;
    }

    /**
     * Unlink {@link Trans#addListener_(ITransListener), the listeners registered by this method
     * live across transactions.
     */
    public void addListener_(ITransListener l)
    {
        _listeners.add(l);
    }

    /**
     * Only a single ongoing transaction is allowed. It's partly because SQLite doesn't support
     * nested transactions, and partly because the Core is single threaded.
     */
    public Trans begin_()
    {
        assert !hasOngoingTransaction_() : "ongoing trans in \n"
                + Util.getThreadStackTrace(_ongoingThread) + "\n===";
        _ongoing = _factTrans.create_(this);
        _ongoingThread = Thread.currentThread();
        return _ongoing;
    }

    /**
     * @return true if there are transactions objects created but not end_()'ed.
     */
    public boolean hasOngoingTransaction_()
    {
        return _ongoing != null && !_ongoing.ended_();
    }

    void committing_(Trans t) throws SQLException
    {
        for (ITransListener l : _listeners) l.committing_(t);
    }

    void committed_()
    {
        // call the listeners in the reverse order of registration
        for (int i = _listeners.size() - 1; i >= 0; i--) _listeners.get(i).committed_();
    }

    void aborted_()
    {
        // call the listeners in the reverse order of registration
        for (int i = _listeners.size() - 1; i >= 0; i--) _listeners.get(i).aborted_();
    }
}
