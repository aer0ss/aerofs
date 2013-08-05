/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.netty.BootstrapFactoryUtil.FrameParams;
import com.aerofs.daemon.transport.netty.TransportMessage;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Transport.PBTPHeader;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static com.aerofs.daemon.transport.lib.PulseManager.newCheckPulseReply;

public class TransportProtocolHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(TransportProtocolHandler.class);

    private final ITransportImpl _tp;
    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final StreamManager _sm;
    private final PulseManager _pm;

    public TransportProtocolHandler(ITransportImpl tp, IBlockingPrioritizedEventSink<IEvent> sink,
            StreamManager sm, PulseManager pm)
    {
        _sink = sink;
        _tp = tp;
        _sm = sm;
        _pm = pm;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof TransportMessage)) return;
        final TransportMessage message = (TransportMessage) e.getMessage();

        Endpoint ep = new Endpoint(_tp, message.getDID());

        try {
            if (message.isPayload()) {
                processUnicastPayload(message, ep);
            } else {
                processUnicastControl(message.getHeader(), ep);
            }
        } catch (Exception ex) {
            l.warn("Ignoring packet from {}. Reason:", ep, LogUtil.suppress(ex, ExDeviceOffline.class,
                    ExNoResource.class, ExProtocolError.class));
        }
    }

    private void processUnicastPayload(TransportMessage event, Endpoint ep)
            throws Exception
    {
        int wirelen = event.getPayload().available() + FrameParams.HEADER_SIZE;
        PBTPHeader reply = TPUtil.processUnicastPayload(ep, event.getUserID(), event.getHeader(),
                event.getPayload(), wirelen, _sink, _sm);

        sendControl(ep.did(), reply);
    }

    private void processUnicastControl(PBTPHeader hdr, Endpoint ep)
            throws ExNoResource, ExProtocolError, ExDeviceOffline
    {
        PBTPHeader reply = null;

        switch (hdr.getType()) {
        case TRANSPORT_CHECK_PULSE_CALL:
        {
            int pulseid = hdr.getCheckPulse().getPulseId();
            l.info("rcv pulse req msgpulseid:{} d:{}", pulseid, ep);
            reply = newCheckPulseReply(pulseid);
            break;
        }
        case TRANSPORT_CHECK_PULSE_REPLY:
        {
            int pulseid = hdr.getCheckPulse().getPulseId();
            l.info("rcv pulse rep msgpulseid:{} d:{}", pulseid, ep);
            _pm.processIncomingPulseId(ep.did(), pulseid);
            break;
        }
        default:
            reply = TPUtil.processUnicastControl(ep, hdr, _sink, _sm);
        }

        sendControl(ep.did(), reply);
    }

    private void sendControl(DID did, @Nullable PBTPHeader reply)
            throws ExDeviceOffline
    {
        if (reply != null) _tp.ucast().send(did, null, Prio.LO, TPUtil.newControl(reply), null);
    }
}
