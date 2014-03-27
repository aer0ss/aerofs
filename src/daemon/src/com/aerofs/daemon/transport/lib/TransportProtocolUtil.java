package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.net.EOStartPulse;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.event.net.EOTpSubsequentPulse;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIChunk;
import com.aerofs.daemon.event.net.rx.EIStreamAborted;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.daemon.event.net.tx.EOBeginStream;
import com.aerofs.daemon.event.net.tx.EOChunk;
import com.aerofs.daemon.event.net.tx.EOMaxcastMessage;
import com.aerofs.daemon.event.net.tx.EOTxAbortStream;
import com.aerofs.daemon.event.net.tx.EOTxEndStream;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.proto.Transport.PBStream.Type;
import com.aerofs.proto.Transport.PBTPHeader;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

import static com.aerofs.proto.Transport.PBStream.InvalidationReason.STREAM_NOT_FOUND;
import static com.aerofs.proto.Transport.PBTPHeader.Type.DATAGRAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;

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

    /*
     * @param streamId null for non-stream messages
     * @param seq 0 for non-stream messages
     */
    public static byte[][] newPayload(@Nullable StreamID streamId, int seq, byte[] bs)
    {
        PBTPHeader.Builder bdHeader = PBTPHeader.newBuilder();

        if (streamId != null) {
            bdHeader.setType(STREAM)
                    .setStream(PBStream.newBuilder()
                            .setType(Type.PAYLOAD)
                            .setStreamId(streamId.getInt())
                            .setSeqNum(seq));
        } else {
            bdHeader.setType(DATAGRAM);
        }

        return new byte[][] {
            Util.writeDelimited(bdHeader.build()).toByteArray(),
            bs
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
     * @param ep {@link com.aerofs.daemon.event.net.Endpoint} identifying which device/transport sent the
     * unicast packet
     * @param userID the cname-verified user id of the remote peer, or null
     * @param h {@link com.aerofs.proto.Transport.PBTPHeader} containing the
     * {@link com.aerofs.daemon.transport.ITransport} framing header
     * @param is {@link java.io.InputStream} from which the payload should be
     * read <b>IMPORTANT:</b>the header has already been read from this
     * <code>InputStream</code>
     * @param wirelen original length of the received packet, <i>including</i>
     * the transport framing header
     * @param sink {@link IBlockingPrioritizedEventSink} into which the payload should be
     * enqueued for further processing
     * @param sm {@link com.aerofs.daemon.transport.lib.StreamManager} to call and use if the unicast packet is
     * part of a stream
     * @return non-null if the caller must send the returned control message
     * back to the sender
     * @throws Exception if the payload cannot be processed
     */
    public static PBTPHeader processUnicastPayload(Endpoint ep, @Nullable UserID userID, PBTPHeader h,
            InputStream is, int wirelen, IBlockingPrioritizedEventSink<IEvent> sink, StreamManager sm)
        throws Exception
    {
        if (!h.hasStream()) {
            // Datagram
            sink.enqueueThrows(new EIUnicastMessage(ep, userID, is, wirelen), Prio.LO);

        } else {
            // Stream
            PBStream wireStream = h.getStream();
            StreamID streamId = new StreamID(wireStream.getStreamId());

            assert wireStream.hasSeqNum();
            int seq = wireStream.getSeqNum();

            boolean alreadyBegun;
            try {
                alreadyBegun = sm.hasStreamBegun(ep.did(), streamId);
            } catch (ExStreamInvalid e) {
                l.info("stream " + ep.did() + ':' + streamId + " invalid. send rx abort");
                return newAbortIncomingStreamHeader(streamId, e.getReason());
            }

            EIChunk event = alreadyBegun ? new EIChunk(ep, userID, streamId, seq, is, wirelen)
                                         : new EIStreamBegun(ep, userID, streamId, is, wirelen);
            try {
                sink.enqueueThrows(event, Prio.LO);
            } catch (Exception e) {
                l.warn("can't enqueue chunk. abort stream: " + Util.e(e));
                sm.removeIncomingStream(ep.did(), streamId);
                throw e;
            }
        }

        return null;
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
    // FIXME: attempt to polymorphise this set of control-processing methods!
    //

    /**
     * Process a control message that came in on a unicast channel. Control message
     * <strong>MUST NOT</strong> have type <code>PAYLOAD</code>
     *
     * @param ep {@link Endpoint} that sent the control message
     * @param h {@link PBTPHeader} control message itself
     * @param sink queue the {@link com.aerofs.daemon.transport.ITransport} uses
     * to communicate with the {@link com.aerofs.daemon.core.Core}
     * @param sm {@link StreamManager} the {@link com.aerofs.daemon.transport.ITransport} uses to manage streams
     * @return {@link PBTPHeader} response required to the control packet
     * @throws ExProtocolError if the control packet has an unrecognized type and cannot
     * be processed
     * @throws ExNoResource if a task required to process the control packet cannot
     * be enqueued in the <code>Core</code> sink
     */
    // FIXME: shouldn't we enqueue blocking into core?
    public static PBTPHeader processUnicastControl(Endpoint ep, PBTPHeader h,
            IBlockingPrioritizedEventSink<IEvent> sink, StreamManager sm)
        throws ExProtocolError, ExNoResource
    {
        switch (h.getType()) {
        case STREAM:
            assert h.hasStream();
            return processStreamControl(ep, h.getStream(), sink, sm);
        default:
            throw new ExProtocolError(PBTPHeader.Type.class);
        }
    }

    private static PBTPHeader processStreamControl(Endpoint ep, PBStream wireStream,
            IBlockingPrioritizedEventSink<IEvent> sink, StreamManager sm)
            throws ExProtocolError, ExNoResource
    {
        StreamID streamId = new StreamID(wireStream.getStreamId());
        switch (wireStream.getType()) {
        case BEGIN_STREAM:
            l.debug("sender:" + ep + " begins stream:" + streamId);
            sm.newIncomingStream(ep.did(), streamId);
            break;
        case TX_ABORT_STREAM:
            l.warn("sender:" + ep + " aborted stream:" + streamId + " rsn:" + wireStream.getReason());
            sm.removeIncomingStream(ep.did(), streamId);
            // it must be the last statement of the case block as it may throw
            sink.enqueueThrows(new EIStreamAborted(ep, streamId, wireStream.getReason()), Prio.LO);
            break;
        case RX_ABORT_STREAM:
            l.warn("recv'r:" + ep + " aborted stream:" + streamId + " rsn:" + wireStream.getReason());
            sm.removeOutgoingStream(streamId);
            // the core will notice that the stream was removed when sending the next chunk
            break;
        default:
            throw new ExProtocolError(PBTPHeader.Type.class);
        }

        return null;
    }

    public static void setupCommonHandlersAndListeners(
            ITransport transport,
            EventDispatcher dispatcher,
            IScheduler scheduler,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            IStores stores,
            StreamManager streamManager,
            PulseManager pulseManager,
            IUnicastInternal unicast,
            IDevicePresenceService presenceManager)
    {
        dispatcher
            .setHandler_(EOUnicastMessage.class, new HdUnicastMessage(unicast))
            .setHandler_(EOBeginStream.class, new HdBeginStream(streamManager, unicast))
            .setHandler_(EOChunk.class, new HdChunk(streamManager, unicast))
            .setHandler_(EOTxEndStream.class, new HdTxEndStream(streamManager))
            .setHandler_(EORxEndStream.class, new HdRxEndStream(streamManager))
            .setHandler_(EOTxAbortStream.class, new HdTxAbortStream(streamManager, unicast))
            .setHandler_(EOUpdateStores.class, new HdUpdateStores(stores))
            .setHandler_(EOStartPulse.class, new HdStartPulse(scheduler))
            .setHandler_(EOTpSubsequentPulse.class, new HdPulse<EOTpSubsequentPulse>(pulseManager, unicast, new SubsequentPulse(scheduler, pulseManager, unicast)))
            .setHandler_(EOTpStartPulse.class, new HdPulse<EOTpStartPulse>(pulseManager, unicast, new StartPulse(scheduler, pulseManager, presenceManager)))
            .setHandler_(EOTpSubsequentPulse.class, new HdPulse<EOTpSubsequentPulse>(pulseManager, unicast, new SubsequentPulse(scheduler, pulseManager, unicast)));

        pulseManager.addGenericPulseDeletionWatcher(transport, outgoingEventSink);
    }

    public static void setupMulticastHandler(EventDispatcher dispatcher, IMaxcast maxcast)
    {
        dispatcher.setHandler_(EOMaxcastMessage.class, new HdMaxcastMessage(maxcast));
    }

    public static void sessionEnded(Endpoint ep, IBlockingPrioritizedEventSink<IEvent> sink,
            StreamManager sm, boolean outbound, boolean inbound)
    {
        l.debug("{} closing {} streams ob:{} ib:{}", ep.did(), ep.tp(), outbound, inbound);

        if (outbound) {
            sm.removeAllOutgoingStreams(ep.did());
            // don't signal stream abortion here because we expect the
            // transport to return errors for all outgoing chunks which in turn
            // forces the core to abort these streams.
        }

        try {
            if (inbound) {
                for (StreamID sid : sm.removeAllIncomingStreams(ep.did())) {
                    l.info("{} remove in stream sid:{}", ep.did(), sid);
                    sink.enqueueThrows(new EIStreamAborted(ep, sid, STREAM_NOT_FOUND), Prio.LO);
                }
            }
        } catch (ExNoResource e) {
            l.warn("enqueue sessionEnded: " + Util.e(e));
        }
    }

    public static boolean isPayload(PBTPHeader transportHeader)
    {
        PBTPHeader.Type type = transportHeader.getType();

        return (type == STREAM && transportHeader.getStream().getType() == Type.PAYLOAD) || type == DATAGRAM;
    }
}
