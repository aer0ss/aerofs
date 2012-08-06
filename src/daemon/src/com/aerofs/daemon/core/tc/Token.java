package com.aerofs.daemon.core.tc;

import java.util.HashSet;

import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExTimeout;

public class Token {
    private final TokenManager _tokenManager;
    private final TC _tc;
    private final Cat _cat;
    private final Prio _prio;

    // this _tcb1 is to optimize for the typical case where only one thread is
    // using the token
    private TCB _tcb1;
    private HashSet<TCB> _tcbs;

    private boolean _reclaimed;
    private final String _reason;

    Token(TokenManager tokenManager, TC tc, Cat cat, Prio prio, String reason)
    {
        assert reason != null && !reason.isEmpty();
        _tokenManager = tokenManager;
        _tc = tc;
        _cat = cat;
        _prio = prio;
        _reason = reason;
    }

    void addTCB_(TCB tcb)
    {
        if (_tcb1 == null) {
            _tcb1 = tcb;
        } else {
            if (_tcbs == null) _tcbs = new HashSet<TCB>();
            Util.verify(_tcbs.add(tcb));
        }
    }

    void removeTCB_(TCB tcb)
    {
        if (_tcb1 == tcb) {
            _tcb1 = null;
        } else {
            Util.verify(_tcbs.remove(tcb));
        }
    }

    Cat getCat()
    {
        return _cat;
    }

    public void reclaim_()
    {
        reclaim_(true);
    }

    void reclaim_(boolean notifyReclaimListener)
    {
        if (_reclaimed) return;

        _tokenManager.reclaim_(this, _prio, notifyReclaimListener);
        _reclaimed = true;

        if (_tcb1 != null) {
            Exception e = new Exception("token reclaimed");
            _tcb1.abort_(e);
        }

        if (_tcbs != null) {
            Exception e = new Exception("token reclaimed");
            for (TCB tcb : _tcbs) tcb.abort_(e);
        }
    }

    boolean isReclaimed_()
    {
        return _reclaimed;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(_cat + " " + _reason + " = ");

        boolean added = false;
        if (_tcb1 != null) {
            sb.append(_tcb1);
            sb.append(", ");
            added = true;
        }

        if (_tcbs != null) {
            for (TCB tcb : _tcbs) {
                sb.append(tcb);
                sb.append(", ");
                added = true;
            }
        }

        if (added) sb.delete(sb.length() - 2, sb.length());

        return sb.toString();
    }

    /**
     * N.B. core lock will be released after pseudoPause. so DON'T TOUCH GLOBAL
     * STATES before calling pseudoResumed!
     *
     * N.B.2 pseudo paused threads cannot be aborted, although they may throw
     * ExAborted when calling pseudoResumed.
     *
     * usage:
     *
     *  TCB tcb = pseudoPause_(...);
     *  try {
     *      ...
     *  } finally {
     *      tcb.pseudoResumed_();
     *  }
     * @param reason TODO
     */
    public TCB pseudoPause_(String reason) throws ExAborted
    {
        return _tc.pseudoPauseImpl_(this, reason);
    }

    public void pause_(String reason) throws ExAborted
    {
        _tc.pauseImpl_(this, reason);
    }

    /**
     * pause for at most timeout
     * @throws ExTimeout if timeout
     */
    public void pause_(long timeout, String reason) throws ExTimeout, ExAborted
    {
        _tc.pauseImpl_(this, timeout, reason);
    }

    public void sleep_(long timeout, String reason) throws ExAborted
    {
        _tc.sleepImpl_(this, timeout, reason);
    }
}
