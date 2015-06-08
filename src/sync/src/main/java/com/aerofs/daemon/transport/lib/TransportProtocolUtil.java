package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.transport.lib.handlers.HdRxEndStream;
import com.aerofs.daemon.transport.lib.handlers.HdUnicastMessage;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.proto.Transport.PBStream.Type;
import com.aerofs.proto.Transport.PBTPHeader;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

import static com.aerofs.proto.Transport.PBTPHeader.Type.DATAGRAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class provides a number of thread-safe {@link com.aerofs.daemon.transport.ITransport} and
 * {@link IUnicast} utility functions
 */
public abstract class TransportProtocolUtil
{
    private static final Logger l = Loggers.getLogger(TransportProtocolUtil.class);

    private TransportProtocolUtil() { } // to prevent instantiation

    public static byte[][] newDatagramPayload(byte[] data)
    {
        PBTPHeader header = PBTPHeader.newBuilder().setType(DATAGRAM).build();
        return new byte[][] {
            Util.writeDelimited(header).toByteArray(),
            data
        };
    }

    public static byte[][] newStreamPayload(StreamID streamId, int seqNum, byte[] data)
    {
        PBTPHeader header = PBTPHeader
                .newBuilder()
                .setType(STREAM)
                .setStream(PBStream.newBuilder()
                        .setType(Type.PAYLOAD)
                        .setStreamId(streamId.getInt())
                        .setSeqNum(seqNum))
                .build();

        return new byte[][] {
            Util.writeDelimited(header).toByteArray(),
            data
        };
    }

    /**
     * Generate a <code>byte[][]</code> for control messages.
     * Control messages are those with non PAYLOAD types.
     *
     * @param h {@link PBTPHeader} object with {@link PBTPHeader.Type} != PAYLOAD
     * @return serialized protobuf object
     */
    public static byte[][] newControl(PBTPHeader h)
    {
        assert (h.getType() != DATAGRAM);
        if (h.hasStream()) {
            assert h.getStream().getType() != Type.PAYLOAD;
        }

        return new byte[][] {
            Util.writeDelimited(h).toByteArray(),
        };
    }

    public static PBTPHeader processUnicastHeader(InputStream is)
        throws IOException
    {
        return PBTPHeader.parseDelimitedFrom(is);
    }

    /**
     * Process the contents of a unicast packet addressed to this peer once its
     * framing header is removed
     *
     * @param ep {@link Endpoint} identifying which device/transport sent the
     * unicast packet
     * @param userID the cname-verified user id of the remote peer, or null
     * @param h {@link PBTPHeader} containing the
     * {@link com.aerofs.daemon.transport.ITransport} framing header
     * @param is {@link InputStream} from which the payload should be
     * read <b>IMPORTANT:</b>the header has already been read from this
     * <code>InputStream</code>
     * @param wirelen original length of the received packet, <i>including</i>
     * the transport framing header
     * @param channel
     *@param sink {@link IBlockingPrioritizedEventSink} into which the payload should be
     * enqueued for further processing
     * @param sm {@link StreamManager} to call and use if the unicast packet is
 * part of a stream   @return non-null if the caller must send the returned control message
     * back to the sender
     * @throws Exception if the payload cannot be processed
     */
    public static PBTPHeader processUnicastPayload(Endpoint ep, @Nullable UserID userID, PBTPHeader h,
                                                   ChannelBuffer is, int wirelen, Channel channel, IBlockingPrioritizedEventSink<IEvent> sink, StreamManager sm)
        throws Exception
    {
        if (!h.hasStream()) {
            // Datagram
            sink.enqueueThrows(new EIUnicastMessage(ep, userID, new ChannelBufferInputStream(is), wirelen), Prio.LO);
        } else {
            // Stream
            PBStream wireStream = h.getStream();
            StreamID streamId = new StreamID(wireStream.getStreamId());

            checkArgument(wireStream.hasSeqNum());
            int seq = wireStream.getSeqNum();

            try {
                processStreamPayload(ep, userID, is, wirelen, channel, sink, sm, streamId, seq);
            } catch (ExStreamInvalid e) {
                l.warn("{} stream {} over {} invalid - send rx abort", ep.did(), streamId, ep.tp());
                return newAbortIncomingStreamHeader(streamId, e.getReason());
            }
        }

        return null;
    }

    private static void processStreamPayload(Endpoint ep, @Nullable UserID userID, ChannelBuffer is,
            int wirelen, Channel channel, IBlockingPrioritizedEventSink<IEvent> sink,
            StreamManager sm, StreamID streamId, int seq) throws ExStreamInvalid, ExNoResource {
        StreamKey sk = new StreamKey(ep.did(), streamId);
        IncomingStream strm = sm.getIncomingStream(sk);
        checkState(strm._channel == channel);

        if (strm.begin()) {
            try {
                l.trace("{} first stream chunk", channel);
                strm.offer(is);
                sink.enqueueThrows(new EIStreamBegun(ep, userID, streamId, strm, wirelen), Prio.LO);
            } catch (Exception e) {
                l.warn("{} fail enqueue chunk for stream {} over {} cause:{}", ep.did(), streamId, ep.tp(), e);
                sm.removeIncomingStream(sk, InvalidationReason.INTERNAL_ERROR);
                throw e;
            }
        } else {
            strm.offer(seq, is);
        }
    }

    public static PBTPHeader newAbortIncomingStreamHeader(StreamID streamId, InvalidationReason reason)
    {
        return PBTPHeader.newBuilder()
                .setType(STREAM)
                .setStream(PBStream.newBuilder()
                        .setType(Type.RX_ABORT_STREAM)
                        .setStreamId(streamId.getInt())
                        .setReason(reason))
                .build();
    }

    public static PBTPHeader newPauseIncomingStreamHeader(StreamID streamId)
    {
        return PBTPHeader.newBuilder()
                .setType(STREAM)
                .setStream(PBStream.newBuilder()
                        .setType(Type.PAUSE_STREAM)
                        .setStreamId(streamId.getInt()))
                .build();
    }

    public static PBTPHeader newResumeIncomingStreamHeader(StreamID streamId)
    {
        return PBTPHeader.newBuilder()
                .setType(STREAM)
                .setStream(PBStream.newBuilder()
                        .setType(Type.RESUME_STREAM)
                        .setStreamId(streamId.getInt()))
                .build();
    }

    public static PBTPHeader newAbortOutgoingStreamHeader(StreamID streamId, InvalidationReason reason)
    {
        return PBTPHeader.newBuilder()
                .setType(STREAM)
                .setStream(PBStream.newBuilder()
                        .setType(Type.TX_ABORT_STREAM)
                        .setStreamId(streamId.getInt())
                        .setReason(reason))
                .build();
    }

    //
    // FIXME: attempt to polymorphize this set of control-processing methods!
    //

    /**
     * Process a control message that came in on a unicast channel. Control message
     * <strong>MUST NOT</strong> have type <code>PAYLOAD</code>
     *
     * @param ep {@link Endpoint} that sent the control message
     * @param h {@link PBTPHeader} control message itself
     * @param channel
     * @param sm {@link StreamManager} the {@link com.aerofs.daemon.transport.ITransport} uses to manage streams   @return {@link PBTPHeader} response required to the control packet
     * @throws ExProtocolError if the control packet has an unrecognized type and cannot
     * be processed
     * @throws ExNoResource if a task required to process the control packet cannot
     * be enqueued in the <code>Core</code> sink
     */
    public static PBTPHeader processUnicastControl(Endpoint ep, PBTPHeader h,
                                                   Channel channel, StreamManager sm)
        throws ExProtocolError, ExNoResource
    {
        switch (h.getType()) {
        case STREAM:
            assert h.hasStream();
            return processStreamControl(ep, h.getStream(), channel, sm);
        default:
            throw new ExProtocolError(PBTPHeader.Type.class);
        }
    }

    private static PBTPHeader processStreamControl(Endpoint ep, PBStream wireStream,
                                                   Channel channel, StreamManager sm)
            throws ExProtocolError, ExNoResource
    {
        StreamID streamId = new StreamID(wireStream.getStreamId());
        StreamKey sk = new StreamKey(ep.did(), streamId);
        switch (wireStream.getType()) {
        case BEGIN_STREAM:
            l.debug("{} begin stream {} over {}", ep.did(), streamId, ep.tp());
            sm.newIncomingStream(sk, channel);
            break;
        case TX_ABORT_STREAM:
            l.warn("{} aborted sending stream {} over {} rsn:{}", ep.did(), streamId, ep.tp(), wireStream.getReason());
            sm.removeIncomingStream(sk, wireStream.getReason());
            break;
        case RX_ABORT_STREAM:
            l.warn("{} aborted receiving stream {} over {} rsn:{}", ep.did(), streamId, ep.tp(), wireStream.getReason());
            OutgoingStream os = sm.removeOutgoingStream(sk);
            if (os != null) os.fail(wireStream.getReason());
            // the core will notice that the stream was removed when sending the next chunk
            break;
        case PAUSE_STREAM:
            l.info("{} pause outgoing stream {} over {}", ep.did(), streamId, ep.tp());
            sm.pauseOutgoingStream(sk);
            break;
        case RESUME_STREAM:
            l.info("{} resume outgoing stream {} over {}", ep.did(), streamId, ep.tp());
            sm.resumeOutgoingStream(sk);
            break;
        default:
            throw new ExProtocolError(PBTPHeader.Type.class);
        }

        return null;
    }

    public static void setupCommonHandlersAndListeners(
            EventDispatcher dispatcher,
            StreamManager streamManager,
            IUnicast unicast)
    {
        dispatcher
            .setHandler_(EOUnicastMessage.class, new HdUnicastMessage(unicast))
            .setHandler_(EORxEndStream.class, new HdRxEndStream(streamManager));
    }

    public static void sessionEnded(Endpoint ep, IBlockingPrioritizedEventSink<IEvent> sink,
            StreamManager sm, boolean outbound, boolean inbound)
    {
        l.debug("{} closing streams ob:{} ib:{} for {}", ep.did(), outbound, inbound, ep.tp());

        if (outbound) {
            sm.removeAllOutgoingStreams(ep.did());
            // don't signal stream abortion here because we expect the
            // transport to return errors for all outgoing chunks which in turn
            // forces the core to abort these streams.
        }

        if (inbound) {
            for (StreamID streamID : sm.removeAllIncomingStreams(ep.did())) {
                l.info("{} remove incoming stream {} over {}", ep.did(), streamID, ep.tp());
            }
        }
    }

    public static boolean isPayload(PBTPHeader transportHeader)
    {
        PBTPHeader.Type type = transportHeader.getType();

        return (type == STREAM && transportHeader.getStream().getType() == Type.PAYLOAD) || type == DATAGRAM;
    }
}
