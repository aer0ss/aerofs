/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.net.link.ILinkStateListener;
import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.NetworkInterface;
import java.util.Map;

import static com.aerofs.daemon.core.CoreUtil.typeString;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

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

    private final NSL _nsl;
    private final DevicePresence _dp;

    private final Map<Integer, MapEntry> _waiters = Maps.newTreeMap();

    @Inject
    public RPC(DevicePresence dp, NSL nsl, LinkStateService lss)
    {
        _dp = dp;
        _nsl = nsl;

        lss.addListener_(new ILinkStateListener() {
            @Override
            public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
                    ImmutableSet<NetworkInterface> removed,
                    ImmutableSet<NetworkInterface> current,
                    ImmutableSet<NetworkInterface> previous)
            {
                if (current.isEmpty()) linkDown_();
            }
        }, sameThreadExecutor());
    }

    /**
     * The device that replies the rpc is added (back) to To in case the To
     * will be later reused
     * @param to the id of the replying device will be added (back) to this parameter
     */
    private DigestedMessage recvReply_(int rpcid, @Nullable To to, Token tk, String reason)
        throws ExTimeout, ExAborted, ExNoResource
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

        if (to != null) to.add_(reply.did());

        return reply;
    }

    /**
     * it tries all destinations specified in To until one of them succeeds or
     * a non-timeout error occurs
     */
    public DigestedMessage do_(To to, SIndex sidx, PBCore call, Token tk, String reason)
        throws Exception
    {
        while (true) {
            Endpoint ep = null;
            try {
                ep = _nsl.send_(to, sidx, call);
                return recvReply_(call.getRpcid(), to, tk, reason);
            } catch (ExTimeout e) {
                handleTimeout_(call, ep, e);
            }
        }
    }

    public DigestedMessage do_(DID did, SIndex sidx, PBCore call, Token tk, String reason)
        throws Exception
    {
        Endpoint ep = null;
        try {
            ep = _nsl.sendUnicast_(did, sidx, call);
            return recvReply_(call.getRpcid(), null, tk, reason);
        } catch (ExTimeout e) {
            handleTimeout_(call, ep, e);
            throw e;
        }
    }

    private void handleTimeout_(PBCore call, Endpoint ep, ExTimeout e)
    {
        l.warn(ep + " " + typeString(call) + " timeout");
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
