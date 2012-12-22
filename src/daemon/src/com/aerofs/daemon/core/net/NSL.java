/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.device.Device;
import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
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
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.Collection;

/**
 * NSL: Network Strategic Layer, responsible for routing messages to the most appropriate devices
 * and transports.
 */
public class NSL
{
    private static final Logger l = Util.l(NSL.class);

    private final MaxcastFilterSender _mcfs = new MaxcastFilterSender();
    private Metrics _m;
    private UnicastInputOutputStack _stack;
    private DevicePresence _dp;
    private Transports _tps;
    private TC _tc;
    private IMapSIndex2SID _sidx2sid;
    private MapSIndex2Store _sidx2s;

    @Inject
    public void inject_(TC tc, Transports tps, DevicePresence dp, UnicastInputOutputStack stack,
            Metrics m, IMapSIndex2SID sidx2sid, MapSIndex2Store sidx2s)
    {
        _tc = tc;
        _tps = tps;
        _dp = dp;
        _stack = stack;
        _m = m;
        _sidx2sid = sidx2sid;
        _sidx2s = sidx2s;
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

        EOMaxcastMessage ev = new EOMaxcastMessage(_sidx2sid.getThrows_(sidx),
                _mcfs.getNewMCastID_(), bs);
        for (ITransport tp : _tps.getAll_()) {
            tp.q().enqueueThrows(ev, _tc.prio());

            // TODO: The current MaxcastFilter implementation will only
            // filter messages sent through the EOMaxcastMessage event.
            // The following code will not be filtered, and can result
            // in redundant messages processed by the core.
            // - markj

            Collection<DID> muod = tp.getMulticastUnreachableOnlineDevices();

            // FIXME: tng branch
            // Future<ImmutableSet<DID>> f = tp.getMaxcastUnreachableOnlineDevices_();
            // FutureBasedCoreIMC.blockingWaitForResult_(f, _tc, Cat.UNLIMITED, "muod"); // FIXME: is this correct?

            if (bs.length > _m.getMaxUnicastSize_()) {
                l.warn("mc-muod too large. drop " + bs.length);
            } else {
                Store s = _sidx2s.get_(sidx);
                for (DID did : muod) {
                    assert !did.equals(Cfg.did());
                    if (s.isOnlinePotentialMemberDevice_(did)) {
                        l.debug("mc-muod " + sidx + " -> " + did);
                        sendUnicast_(new Endpoint(tp, did), type, rpcid, sidx, bs);
                    }
                }
            }
        }

// SP Daemon support is temporarily disabled. Search the code base for "SP_DID" and references to
// Cfg.isSP() when restoring the function.
//
//        if (_sidx2s.get_(sidx).getOnlinePotentialMemberDevices_().isEmpty() &&
//                !Cfg.did().equals(SP_DID)) {
//            // manually send a maxcast to SP if the store has no member devices yet. this is a
//            // workaround for the situation where a device joins a store and the only online device
//            // is the SP. because the SP doesn't listen to XMPP multicast and it's not a MUOD for
//            // TCP, there is no other way to reach SP.
//            //
//            // BUGBUG if the peer issues two client messages within DTLS handshake
//            // timeout, and SP joins the store (from other peers) after the first message
//            // and before the second message, because SP ignores the DTLS handshake
//            // request issued for the first message, the second message will be
//            // queued up until handshake times out at the client side.
//            //
//            l.info("mc " + sidx + " -> sp");
//            sendUnicast_(SP_DID, type, rpcid, sidx, bs);
//        }
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
