/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.tx.EOMaxcastMessage;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.MaxcastFilterSender;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.rocklog.RockLog;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Simple {maxcast,unicast}-routing layer. Given a DID or an SID this layer
 * decides over <em>which</em> transport the message should be sent.
 */
public class TransportRoutingLayer
{
    private static final Logger l = Loggers.getLogger(TransportRoutingLayer.class);

    private final MaxcastFilterSender _mcfs = new MaxcastFilterSender();

    private DevicePresence _dp;
    private Transports _tps;
    private UnicastInputOutputStack _stack;
    private RockLog _rockLog;

    @Inject
    public void inject_(DevicePresence dp, Transports tps, UnicastInputOutputStack stack, RockLog rockLog)
    {
        _dp = dp;
        _tps = tps;
        _stack = stack;
        _rockLog = rockLog;
    }

    // FIXME(AG): do _not_ use because this layer does not have the concept of available transports that we've heard from but don't know if they work or not
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
        return sendUnicast_(did, CoreUtil.typeString(pb), pb.getRpcid(), Util.writeDelimited(pb));
    }

    /**
     * Send a core PB to the DID over a specific transport.
     *
     * @return endpoint (DID, transport) over which the message was sent, or null if it was dropped
     */
    public @Nullable Endpoint sendUnicast_(Endpoint ep, PBCore pb)
            throws Exception
    {
        return sendUnicast_(ep, CoreUtil.typeString(pb), pb.getRpcid(), Util.writeDelimited(pb)) ? ep : null;
    }

    /**
     * Send an RPC message to the DID over any available transport.
     *
     * @return endpoint (DID, transport) over which the message was sent, or null if it was dropped
     */
    public @Nullable Endpoint sendUnicast_(DID did, String type, int rpcid, ByteArrayOutputStream os)
            throws Exception
    {
        checkArgument(!did.equals(Cfg.did()), "cannot send unicast to self");

        Device dev = _dp.getOPMDevice_(did);
        if (dev == null) { // there's no online device, so we drop the packet
            return null;
        }

        Endpoint using = new Endpoint(dev.getPreferredTransport_(), did);
        return sendUnicast_(using, type, rpcid, os) ? using : null;
    }

    public boolean sendUnicast_(Endpoint ep, String type, int rpcid, ByteArrayOutputStream os)
            throws Exception
    {
        byte[] bs = os.toByteArray();
        if (bs.length > DaemonParam.MAX_UNICAST_MESSAGE_SIZE) {
            l.warn("packet > max uc size - dropping");
            _rockLog.newDefect("net.unicast.overflow").addData("message_size", bs.length).send();
            return false;
        }

        l.debug("{},{} -> {}", type, rpcid, ep);
        _stack.output().sendUnicastDatagram_(bs, ep);

        return true;
    }

    /**
     * Send a maxcast message to all devices interested in a store over all maxcast transports.
     */
    public void sendMaxcast_(SID sid, String type, int rpcid, ByteArrayOutputStream os)
            throws Exception
    {
        l.debug("mc {},{} -> {}", type, rpcid, sid);

        EOMaxcastMessage ev = new EOMaxcastMessage(sid, _mcfs.getNewMCastID_(), os.toByteArray());
        for (ITransport tp : _tps.getAll_()) {
            if (!tp.supportsMulticast()) continue;
            tp.q().enqueueThrows(ev, TC.currentThreadPrio());
        }
    }
}
