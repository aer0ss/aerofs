package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.util.Map;

/**
 * RPC: Remote Procedure Calls
 */
public class RPC
{
    private static final Logger l = Util.l(RPC.class);

    private static class MapEntry
    {
        TCB _tcb;
        DigestedMessage _reply;
    }

    private final NSL _nsl;
    private final DevicePresence _dp;

    private final Map<Integer, MapEntry> _waiters = Maps.newTreeMap();

    @Inject
    public RPC(DevicePresence dp, NSL nsl)
    {
        _dp = dp;
        _nsl = nsl;
    }

    /**
     * The device that replies the rpc is added (back) to To in case the To
     * will be later reused
     * @param to the id of the replying device will be added (back) to this
     * parameter. it can be null
     */
    private DigestedMessage recvReply_(int rpcid, To to, Token tk, String reason)
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

        l.info("got reply");

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
                if (ep != null) _dp.startPulse_(ep.tp(), ep.did());
                l.warn(ep + " timeout. try next");
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
            if (ep != null) _dp.startPulse_(ep.tp(), ep.did());
            throw e;
        }
    }

    /**
     * @return false if the reply is spurious or is ignored
     */
    public boolean processReply_(DigestedMessage msg) throws ExProtocolError
    {
        if (!msg.pb().hasRpcid()) throw new ExProtocolError("missing rpcid");
        int rpcid = msg.pb().getRpcid();

        MapEntry me = _waiters.remove(rpcid);

        if (me != null) {
            me._reply = msg;
            return me._tcb.resume_();
        } else {
            l.info("spurious reply " + rpcid);
            return false;
        }
    }
}
