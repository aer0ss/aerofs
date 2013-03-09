/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.protocol.IDownloadCompletionListener;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.lib.id.SOCID;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Map;
import java.util.Set;

/**
 * Helper class for Collector tests to simulate various behaviors of the download subsystem
 */
public class MockAsyncDownload implements Answer<Boolean>
{
    private boolean _continuation;

    private SOCID _socid;
    private ITokenReclamationListener _trl;
    private IDownloadCompletionListener _dcl;

    @SuppressWarnings("unchecked")
    @Override
    public Boolean answer(InvocationOnMock invocation) throws Throwable
    {
        Object[] args = invocation.getArguments();
        SOCID socid = (SOCID)args[0];
        Set<DID> dids = (Set<DID>)args[1];
        IDownloadListenerFactory fact = (IDownloadListenerFactory)args[2];
        ITokenReclamationListener listener = (ITokenReclamationListener)args[3];
        return simulateDownload(socid, dids, fact.create_(dids, null), listener);
    }

    private boolean simulateDownload(SOCID socid, Set<DID> dids,
            IDownloadCompletionListener dcl, ITokenReclamationListener trl)
    {
        if (_continuation) {
            _trl = trl;
            _dcl = null;
            return false;
        }

        _socid = socid;

        _trl = null;
        _dcl = dcl;
        return true;
    }

    /**
     * Simulate token reclamation
     * @pre the object must have been used to simulate a token starvation in the previous simulation
     */
    public void reclaim()
    {
        ITokenReclamationListener trl = _trl;
        _continuation = false;
        _trl = null;
        trl.tokenReclaimed_();
    }

    /**
     * Called on successful download
     */
    protected void completed() {}

    /**
     * simulate continuation request (i.e token starvation)
     */
    void outOfToken() { _continuation = true; }

    /**
     * Simulate successful download from the given device
     */
    void ok(DID sender) { _dcl.okay_(_socid, sender); completed(); }

    /**
     * Simulate download failure with a general error
     */
    void fail(Exception e) { _dcl.onGeneralError_(_socid, e); }

    /**
     * Simulate download failure with per-device errors
     */
    void fail(Map<DID, Exception> d2e) { _dcl.onPerDeviceErrors_(_socid, d2e); }
}
