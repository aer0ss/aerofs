/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.tx.EOMaxcastMessage;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.MaxcastFilterSender;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SIndex;
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
    private TC _tc;
    private IMapSIndex2SID _sidx2sid;

    @Inject
    public void inject_(TC tc, Transports tps, DevicePresence dp, UnicastInputOutputStack stack,
            Metrics m, IMapSIndex2SID sidx2sid)
    {
        _tc = tc;
        _tps = tps;
        _dp = dp;
        _stack = stack;
        _m = m;
        _sidx2sid = sidx2sid;
    }

    //
    // message-transmission methods
    //

    private void sendUnicast_(Endpoint ep, String type, int rpcid, SIndex sidx, byte[] bs)
            throws Exception
    {
        assert !ep.did().equals(Cfg.did());
        l.debug(type + ',' + rpcid + " -> " + ep);
        if (bs.length > _m.getMaxUnicastSize_()) {
            // unicast messages shall never exceeds the limit
            SystemUtil.fatal("uc too large " + bs.length);
        }

        PeerContext pc = new PeerContext(ep, sidx);

        _stack.output().sendUnicastDatagram_(bs, pc);
    }

    /**
     * send to the device's preferred transport if the device is online; send to
     * all transports otherwise.
     */
    private @Nullable Endpoint sendUnicast_(DID did, String type, int rpcid, SIndex sidx, byte[] bs)
            throws Exception
    {
        Device dev = _dp.getOPMDevice_(did);
        if (dev != null) {
            Endpoint ep = new Endpoint(dev.getPreferedTransport_(), did);
            sendUnicast_(ep, type, rpcid, sidx, bs);
            return ep;
        } else {
            for (ITransport tp : _tps.getAll_()) {
                sendUnicast_(new Endpoint(tp, did), type, rpcid, sidx, bs);
            }
            return null;
        }
    }

    private void sendMaxcast_(SIndex sidx, String type, int rpcid, byte[] bs)
            throws Exception
    {
        l.debug("mc " + type + ',' + rpcid + " -> " + sidx);

        if (bs.length > _m.getRecommendedMaxcastSize_()) {
            l.debug("mc too large " + bs.length + ". send anyway");
        }

        EOMaxcastMessage ev =
                new EOMaxcastMessage(_sidx2sid.getThrows_(sidx), _mcfs.getNewMCastID_(), bs);

        for (ITransport tp : _tps.getAll_()) {
            if (!tp.supportsMulticast()) continue;

            tp.q().enqueueThrows(ev, _tc.prio());
        }
    }

    private @Nullable Endpoint send_(To to, String type, int rpcid, SIndex sidx, byte[] bs) throws Exception
    {
        // save the string of to, as it may change on pick_()
        String strTo = to.toString();
        DID did = to.pick_();

        if (did != null) {
            l.debug(strTo + '~' + did);
            return sendUnicast_(did, type, rpcid, sidx, bs);
        } else {
            // maxcast
            l.debug(strTo + '~' + to.sidx());
            sendMaxcast_(to.sidx(), type, rpcid, bs);
            return null;
        }
    }

    //
    // adapters and wrappers
    //

    public void sendUnicast_(Endpoint ep, SIndex sidx, PBCore pb)
            throws Exception
    {
        sendUnicast_(ep, CoreUtil.typeString(pb), pb.getRpcid(), sidx,
                Util.writeDelimited(pb).toByteArray());
    }

    public @Nullable Endpoint sendUnicast_(DID did, SIndex sidx, PBCore pb)
            throws Exception
    {
        return sendUnicast_(did, CoreUtil.typeString(pb), pb.getRpcid(), sidx,
                Util.writeDelimited(pb).toByteArray());
    }

    public void sendUnicast_(DID did, SIndex sidx, String type, int rpcid,
            ByteArrayOutputStream os) throws Exception
    {
        sendUnicast_(did, type, rpcid, sidx, os.toByteArray());
    }

    public void sendMaxcast_(SIndex sidx, String type, int rpcid,
            ByteArrayOutputStream os) throws Exception
    {
        sendMaxcast_(sidx, type, rpcid, os.toByteArray());
    }

    /**
     * Send a message to a peer via maxcast <em></>or</em> unicast channel
     *
     * @param to destination for the message
     * @param sidx the sstore to which the message refers
     * @param pb protobuf message to be sent to the peer
     * @return {@link Endpoint} object if the message was unicast to a single device,
     * null if it was maxcast or sent via unicast to multiple devices
     */
    public @Nullable Endpoint send_(To to, SIndex sidx, PBCore pb) throws Exception
    {
        return send_(to, CoreUtil.typeString(pb), pb.getRpcid(), sidx,
                Util.writeDelimited(pb).toByteArray());
    }
}
