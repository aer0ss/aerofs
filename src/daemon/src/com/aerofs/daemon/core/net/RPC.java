/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.protocol.CoreProtocolUtil;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.CoreExecutor;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Core.PBCore;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Map;

/**
 * RPC: Remote Procedure Calls
 */
public class RPC
{
    private static final Logger l = Loggers.getLogger(RPC.class);

    private static class ExLinkDown extends Exception
    {
        private static final long serialVersionUID = 1L;
    }

    private static class ResponseWaiter
    {
        TCB _tcb;
        DigestedMessage _response;
    }

    private final TransportRoutingLayer _trl;

    private final Map<Integer, ResponseWaiter> _waiters = Maps.newTreeMap();

    @Inject
    public RPC(TransportRoutingLayer trl, CoreExecutor coreExecutor, LinkStateService lss)
    {
        _trl = trl;

        lss.addListener((previous, current, added, removed) -> {
            if (current.isEmpty()) {
                linkDown_();
            }
        }, coreExecutor); // want to be notified on the core thread
    }

    private DigestedMessage receiveResponse_(int rpcid, Token tk, String reason)
        throws ExTimeout, ExAborted, ExProtocolError
    {
        assert !_waiters.containsKey(rpcid);

        ResponseWaiter waiter = new ResponseWaiter();
        waiter._tcb = TC.tcb();
        _waiters.put(rpcid, waiter);

        try {
            tk.pause_(Cfg.timeout(), reason);
        } finally {
            _waiters.remove(rpcid);
        }

        DigestedMessage response = waiter._response;
        assert response != null;

        l.debug("got response rid:{} ep:{}", rpcid, response.ep());

        return response;
    }

    public DigestedMessage issueRequest_(DID did, PBCore request, Token tk, String reason)
        throws Exception
    {
        Endpoint ep = null;
        try {
            ep = _trl.sendUnicast_(did, request);
            return receiveResponse_(request.getRpcid(), tk, reason);
        } catch (ExTimeout e) {
           l.warn("timeout rid:{} t:{} ep:{}", request.getRpcid(), CoreProtocolUtil.typeString(request), ep);
            throw e;
        }
    }

    /**
     * @return false if the reply is spurious or is ignored
     */
    public boolean processResponse_(DigestedMessage msg)
            throws ExProtocolError
    {
        if (!msg.pb().hasRpcid()) {
            throw new ExProtocolError("missing rpcid");
        }

        // Remove the entry to prevent further notification on it after this method
        // returns and before the waiting thread acquires the core lock and proceeds execution.
        int rpcid = msg.pb().getRpcid();
        ResponseWaiter waiter = _waiters.remove(rpcid);

        if (waiter != null) {
            waiter._response = msg;
            return waiter._tcb.resume_();
        } else {
            l.warn("spurious response rid:{} ep:{}", rpcid, msg.ep());
            return false;
        }
    }

    /**
     * Abort all pending RPCs. Outgoing and incoming streams also need to be aborted. However,
     * instead of initiating the abortion in the core, it's initiated by the transports since
     * they need to release stream-related resources specific to each transport.
     */
    private void linkDown_()
    {
        if (_waiters.isEmpty()) {
            return;
        }

        Exception e = new ExLinkDown();
        for (ResponseWaiter waiter : _waiters.values()) {
            waiter._tcb.abort_(e);
        }

        // Remove all the entries to prevent further notification on them after this method
        // returns and before the waiting threads acquire the core lock and proceed execution.
        _waiters.clear();
    }
}
