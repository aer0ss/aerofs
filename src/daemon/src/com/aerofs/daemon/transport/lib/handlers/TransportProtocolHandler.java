/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.BootstrapFactoryUtil.FrameParams;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jboss.netty.channel.Channels.future;
import static org.jboss.netty.channel.Channels.write;

/**
 * Decodes a {@link com.aerofs.daemon.transport.lib.handlers.TransportMessage}
 * and performs one/more of the following actions:
 * <ul>
 *     <li>Delivers an event to the core.</li>
 *     <li>Starts/stops a stream for the sending peer.</li>
 *     <li>Sends control messages to the remote peer.</li>
 * </ul>
 * Basically, this class understands {@link com.aerofs.proto.Transport.PBTPHeader}
 * messages and can make appropriate state changes to the transport on receiving them.
 */
@org.jboss.netty.channel.ChannelHandler.Sharable
public final class TransportProtocolHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(TransportProtocolHandler.class);

    private final ITransport transport;
    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventsink;
    private final StreamManager streamManager;

    public TransportProtocolHandler(
            ITransport transport,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventsink,
            StreamManager streamManager)
    {
        this.outgoingEventsink = outgoingEventsink;
        this.transport = transport;
        this.streamManager = streamManager;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof TransportMessage)) {
            return;
        }

        final TransportMessage message = (TransportMessage) e.getMessage();
        Endpoint endpoint = new Endpoint(transport, message.getDID());

        PBTPHeader reply;

        if (message.isPayload()) {
            reply = processUnicastPayload(message, endpoint);
        } else {
            reply = processUnicastControl(message.getHeader(), endpoint);
        }

        if (reply != null) {
            write(ctx, future(e.getChannel()), TransportProtocolUtil.newControl(reply));
        }
    }

    private @Nullable PBTPHeader processUnicastPayload(TransportMessage message, Endpoint endpoint)
            throws Exception
    {
        int wireLength = message.getPayload().available() + FrameParams.HEADER_SIZE;
        return TransportProtocolUtil.processUnicastPayload(
                endpoint,
                message.getUserID(),
                message.getHeader(),
                message.getPayload(),
                wireLength,
                outgoingEventsink,
                streamManager);
    }

    private @Nullable PBTPHeader processUnicastControl(PBTPHeader header, Endpoint endpoint)
            throws ExNoResource, ExProtocolError, ExTransportUnavailable, ExDeviceUnavailable
    {
        PBTPHeader reply = null;

        switch (header.getType()) {
        case TRANSPORT_CHECK_PULSE_CALL:
        {
            // FIXME: remove this block when it seems safe
            l.warn("{} rcv unexpected pulse req over {}", endpoint.did(), endpoint.tp());
            break;
        }
        case TRANSPORT_CHECK_PULSE_REPLY:
        {
            // FIXME: remove this block when it seems safe
            l.info("{} rcv unexpected pulse rep over {}", endpoint.did(), endpoint.tp());
            break;
        }
        default:
        {
            PBTPHeader.Type type = header.getType();
            checkArgument(type == Type.STREAM, "d:%s recv invalid hdr:%s", endpoint.did(), type.name());
            checkArgument(header.getStream().getType() != PBStream.Type.PAYLOAD,
                    "d:%s recv invalid stream hdr type:%s", endpoint.did(), header.getStream().getType());
            reply = TransportProtocolUtil.processUnicastControl(endpoint, header, outgoingEventsink, streamManager);
        }
        }

        return reply;
    }
}
