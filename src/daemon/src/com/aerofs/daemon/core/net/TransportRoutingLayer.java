/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.protocol.CoreProtocolUtil;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.tx.EOMaxcastMessage;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.MaxcastFilterSender;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Core.PBCore;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;

import static com.aerofs.defects.Defects.newDefect;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Simple {maxcast,unicast}-routing layer. Given a DID or an SID this layer
 * decides over <em>which</em> transport the message should be sent.
 */
public class TransportRoutingLayer
{
    private static final Logger l = Loggers.getLogger(TransportRoutingLayer.class);

    private final MaxcastFilterSender _mcfs = new MaxcastFilterSender();

    private DID _localdid;
    private CoreQueue _q;
    private Devices _devices;
    private Transports _tps;
    private UnicastInputOutputStack _stack;

    @Inject
    public void inject_(CfgLocalDID localDID, CoreQueue q, Devices dp, Transports tps,
            UnicastInputOutputStack stack)
    {
        _localdid = localDID.get();
        _q = q;
        _devices = dp;
        _tps = tps;
        _stack = stack;
    }

    // FIXME(AG): do _not_ use because this layer does not have the concept of available transports that we've heard from but don't know if they work or not
    // FIXME JP: do not use the class or this method?
    /**
     * Send a core PB to the DID over any available transport.
     * <strong>DO NOT USE FOR REPLIES!</strong> Use
     * {@link com.aerofs.daemon.core.net.TransportRoutingLayer#sendUnicast_(Endpoint, PBCore)}
     * instead.
     *
     * @return endpoint (DID, transport) over which the message was sent, or null if it was dropped
     */
    public @Nullable Endpoint sendUnicast_(DID did, PBCore pb)
            throws Exception
    {
        return sendUnicast_(did, CoreProtocolUtil.typeString(pb), pb.getRpcid(), Util.writeDelimited(pb));
    }

    /**
     * Send a core PB to the DID over a specific transport.
     *
     * @return endpoint (DID, transport) over which the message was sent, or null if it was dropped
     */
    public @Nullable Endpoint sendUnicast_(Endpoint ep, PBCore pb)
            throws Exception
    {
        return sendUnicast_(ep, CoreProtocolUtil.typeString(pb), pb.getRpcid(), Util.writeDelimited(pb)) ? ep : null;
    }

    /**
     * Send an RPC message to the DID over any available transport.
     *
     * @param type : used by debug trace only
     * @return endpoint (DID, transport) over which the message was sent, or null if it was dropped
     */
    public @Nullable Endpoint sendUnicast_(DID did, String type, int rpcid, ByteArrayOutputStream os)
            throws Exception
    {
        checkArgument(!did.equals(_localdid), "cannot send unicast to self");

        Device dev = _devices.getOPMDevice_(did);
        if (dev == null) { // there's no online device, so we drop the packet
            return null;
        }

        Endpoint using = new Endpoint(dev.getPreferredTransport_(), did);
        return sendUnicast_(using, type, rpcid, os) ? using : null;
    }

    /**
     * @param type : used by debug trace only
     * @throws Exception
     */
    public boolean sendUnicast_(Endpoint ep, String type, int rpcid, ByteArrayOutputStream os)
            throws Exception
    {
        byte[] bs = os.toByteArray();
        if (bs.length > DaemonParam.MAX_UNICAST_MESSAGE_SIZE) {
            l.warn("{} packet too large", ep.did());
            newDefect("net.unicast.overflow")
                    .addData("message_size", bs.length)
                    .sendAsync();
            return false;
        }

        l.debug("{} -> uc {},{} over {}", ep.did(), type, rpcid, ep.tp());
        sendPacketWithFailureCallback_(ep, bs);

        return true;
    }

    /**
     * Send a maxcast message to all devices interested in a store over all maxcast transports.
     */
    public void sendMaxcast_(SID sid, String type, int rpcid, ByteArrayOutputStream os)
            throws Exception
    {
        l.debug("{} -> mc {},{}", sid, type, rpcid);

        EOMaxcastMessage ev = new EOMaxcastMessage(sid, _mcfs.getNextMaxcastId(), os.toByteArray());
        for (ITransport tp : _tps.getAll_()) {
            if (!tp.supportsMulticast()) continue;
            tp.q().enqueueThrows(ev, TC.currentThreadPrio());
        }
    }

    // [sigh] ugh
    //
    // this code is brittle - there are timing issues here
    // it's possible for the waiter to get triggered and for
    // the (device, transport) to be placed into the "pulsing" state
    // meanwhile, the connection could actually be set up
    // properly in the transport layer. this could result in a brief
    // period in which the core believes that the transport is
    // being pulsed while the actual transport connection is fine.
    //
    // plus, this code is horrendous. I hate myself.
    private void sendPacketWithFailureCallback_(final Endpoint ep, byte[] bs)
            throws Exception
    {
        _stack.output().sendUnicastDatagram_(bs, new IResultWaiter()
        {
            @Override
            public void okay()
            {
                // noop
            }

            @Override
            public void error(Exception e)
            {
                _q.enqueueBlocking(new AbstractEBSelfHandling()
                {
                    @Override
                    public void handle_()
                    {
                        // FIXME: Need to understand this guy. Ok, we start pulsing, but if we don't do that, is
                        // there any purpose for this whole containing method?
//                        _devices.startPulse_(ep.tp(), ep.did());
                    }
                }, Prio.LO);
            }
        }, ep);
    }
}
