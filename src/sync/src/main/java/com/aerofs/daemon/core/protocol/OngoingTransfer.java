package com.aerofs.daemon.core.protocol;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.transfers.BaseTransferState;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;

public final class OngoingTransfer extends AbstractEBSelfHandling {
    private volatile boolean _scheduled;
    private volatile boolean _aborted;
    private volatile long _remaining;

    private final SOCID _socid;
    private final Endpoint _ep;
    private final long _totalFileLength;

    private final CoreScheduler _sched;
    private final BaseTransferState _state;
    private boolean _done;

    public enum End {
        SUCCESS,
        FAILURE
    }

    public OngoingTransfer(CoreScheduler sched, BaseTransferState state, Endpoint ep, SOID soid, long totalFileLength) {
        _sched = sched;
        _state = state;
        _socid = new SOCID(soid, CID.CONTENT);
        _ep = ep;
        _totalFileLength = totalFileLength;
    }

    void done_(End end) {
        _done = true;
        _state.ended_(_socid, _ep, end == End.FAILURE);
    }

    void abort() {
        _aborted = true;
    }

    boolean aborted() {
        return _aborted;
    }

    void progress(long remaining) {
        _remaining = remaining;
        if (!_scheduled) {
            _scheduled = true;
            _sched.schedule(this, 0);
        }
    }

    @Override
    public void handle_() {
        if (_done) return;
        _state.progress_(_socid, _ep, _totalFileLength - _remaining, _totalFileLength);
        _scheduled = false;
    }

    public SOID soid() {
        return _socid.soid();
    }
}
