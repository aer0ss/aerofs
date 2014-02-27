/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.CoreExecutor;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Core.PBCore;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.net.NetworkInterface;
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

    private static class MapEntry
    {
        TCB _tcb;
        DigestedMessage _reply;
    }

    private final TransportRoutingLayer _trl;
    private final DevicePresence _dp;

    private final Map<Integer, MapEntry> _waiters = Maps.newTreeMap();

    @Inject
    public RPC(DevicePresence dp, TransportRoutingLayer trl, CoreExecutor coreExecutor, LinkStateService lss)
    {
        _dp = dp;
        _trl = trl;

        lss.addListener(new ILinkStateListener()
        {
            @Override
            public void onLinkStateChanged(
                    ImmutableSet<NetworkInterface> previous,
                    ImmutableSet<NetworkInterface> current,
                    ImmutableSet<NetworkInterface> added,
                    ImmutableSet<NetworkInterface> removed)
            {
                if (current.isEmpty()) linkDown_();
            }
        }, coreExecutor); // want to be notified on the core thread
    }

    private DigestedMessage recvReply_(int rpcid, Token tk, String reason)
        throws ExTimeout, ExAborted, ExProtocolError
    {
        assert !_waiters.containsKey(rpcid);

        MapEntry me = new MapEntry();
        me._tcb = TC.tcb();
        _waiters.put(rpcid, me);

        try {
            tk.pause_(Cfg.timeout(), reason);
        } finally {
            _waiters.remove(rpcid);
        }

        DigestedMessage reply = me._reply;
        assert reply != null;

        l.debug("got reply " + reply.ep());

        return reply;
    }

    public DigestedMessage do_(DID did, PBCore call, Token tk, String reason)
        throws Exception
    {
        Endpoint ep = null;
        try {
            ep = _trl.sendUnicast_(did, call);
            return recvReply_(call.getRpcid(), tk, reason);
        } catch (ExTimeout e) {
            handleTimeout_(call, ep);
            throw e;
        }
    }

    public DigestedMessage do_(DID did, PBCore call, ByteArrayOutputStream out, Token tk, String reason)
            throws Exception
    {
        Endpoint ep = null;
        try {
            ep = _trl.sendUnicast_(did, CoreUtil.typeString(call), call.getRpcid(), out);
            return recvReply_(call.getRpcid(), tk, reason);
        } catch (ExTimeout e) {
            handleTimeout_(call, ep);
            throw e;
        }
    }

    private void handleTimeout_(PBCore call, Endpoint ep)
    {
        l.warn("{} {} timeout", ep, CoreUtil.typeString(call));
        if (ep != null) _dp.startPulse_(ep.tp(), ep.did());
    }

    /**
     * @return false if the reply is spurious or is ignored
     */
    public boolean processReply_(DigestedMessage msg) throws ExProtocolError
    {
        if (!msg.pb().hasRpcid()) throw new ExProtocolError("missing rpcid");
        int rpcid = msg.pb().getRpcid();

        // Remove the entry to prevent further motification on it after this method
        // returns and before the waiting thread acquires the core lock and proceeds execution.
        MapEntry me = _waiters.remove(rpcid);

        if (me != null) {
            me._reply = msg;
            return me._tcb.resume_();
        } else {
            l.debug("spurious reply " + rpcid);
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
        if (_waiters.isEmpty()) return;

        Exception e = new ExLinkDown();
        for (MapEntry me : _waiters.values()) me._tcb.abort_(e);
        // Remove all the entires to prevent further motification on them after this method
        // returns and before the waiting threads acquire the core lock and proceed execution.
        _waiters.clear();
    }
}
