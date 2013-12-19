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
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.MaxcastFilterSender;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Core.PBCore;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;

/**
 * NSL: Network Strategic Layer, responsible for routing messages to the most appropriate devices
 * and transports.
 */
public class NSL
{
    private static final Logger l = Loggers.getLogger(NSL.class);

    private final MaxcastFilterSender _mcfs = new MaxcastFilterSender();
    private Metrics _m;
    private UnicastInputOutputStack _stack;
    private DevicePresence _dp;
    private Transports _tps;


    @Inject
    public void inject_(Transports tps, DevicePresence dp, UnicastInputOutputStack stack,
            Metrics m)
    {
        _tps = tps;
        _dp = dp;
        _stack = stack;
        _m = m;
    }

    //
    // message-transmission methods
    //

    private void sendUnicast_(Endpoint ep, String type, int rpcid, byte[] bs)
            throws Exception
    {
        assert !ep.did().equals(Cfg.did());
        l.debug(type + ',' + rpcid + " -> " + ep);
        if (bs.length > _m.getMaxUnicastSize_()) {
            // unicast messages shall never exceeds the limit
            SystemUtil.fatal("uc too large " + bs.length);
        }

        _stack.output().sendUnicastDatagram_(bs, ep);
    }

    /**
     * send to the device's preferred transport if the device is online; send to
     * all transports otherwise.
     */
    private @Nullable Endpoint sendUnicast_(DID did, String type, int rpcid, byte[] bs)
            throws Exception
    {
        Device dev = _dp.getOPMDevice_(did);
        if (dev != null) {
            Endpoint ep = new Endpoint(dev.getPreferredTransport_(), did);
            sendUnicast_(ep, type, rpcid, bs);
            return ep;
        } else {
            for (ITransport tp : _tps.getAll_()) {
                sendUnicast_(new Endpoint(tp, did), type, rpcid, bs);
            }
            return null;
        }
    }

    private void sendMaxcast_(SID sid, String type, int rpcid, byte[] bs)
            throws Exception
    {
        l.debug("mc " + type + ',' + rpcid + " -> " + sid);

        if (bs.length > _m.getRecommendedMaxcastSize_()) {
            l.debug("mc too large " + bs.length + ". send anyway");
        }

        EOMaxcastMessage ev = new EOMaxcastMessage(sid, _mcfs.getNewMCastID_(), bs);

        for (ITransport tp : _tps.getAll_()) {
            if (!tp.supportsMulticast()) continue;

            tp.q().enqueueThrows(ev, TC.currentThreadPrio());
        }
    }

    //
    // adapters and wrappers
    //

    public void sendUnicast_(Endpoint ep, PBCore pb) throws Exception
    {
        sendUnicast_(ep, CoreUtil.typeString(pb), pb.getRpcid(), Util.writeDelimited(pb).toByteArray());
    }

    public @Nullable Endpoint sendUnicast_(DID did, PBCore pb) throws Exception
    {
        return sendUnicast_(did, CoreUtil.typeString(pb), pb.getRpcid(), Util.writeDelimited(pb).toByteArray());
    }

    public Endpoint sendUnicast_(DID did, String type, int rpcid,
            ByteArrayOutputStream os) throws Exception
    {
        return sendUnicast_(did, type, rpcid, os.toByteArray());
    }

    public void sendMaxcast_(SID sid, String type, int rpcid,
            ByteArrayOutputStream os) throws Exception
    {
        sendMaxcast_(sid, type, rpcid, os.toByteArray());
    }
}
