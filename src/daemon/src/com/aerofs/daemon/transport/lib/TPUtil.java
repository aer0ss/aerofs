package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.EOLinkStateChanged;
import com.aerofs.daemon.event.net.EOStartPulse;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.event.net.EOTpSubsequentPulse;
import com.aerofs.daemon.event.net.EOTransportFlood;
import com.aerofs.daemon.event.net.EOTransportFloodQuery;
import com.aerofs.daemon.event.net.EOTransportPing;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIChunk;
import com.aerofs.daemon.event.net.rx.EISessionEnded;
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
import com.aerofs.daemon.transport.lib.TransportDiagnosisState.FloodEntry;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.proto.Transport.PBStream.Type;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTransportDiagnosis;
import com.aerofs.proto.Transport.PBTransportDiagnosis.PBPing;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

import static com.aerofs.proto.Transport.PBStream.InvalidationReason.STREAM_NOT_FOUND;
import static com.aerofs.proto.Transport.PBTPHeader.Type.DATAGRAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.DIAGNOSIS;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.aerofs.proto.Transport.PBTransportDiagnosis.PBFloodStatReply;
import static com.aerofs.proto.Transport.PBTransportDiagnosis.Type.FLOOD_STAT_REPLY;
import static com.aerofs.proto.Transport.PBTransportDiagnosis.Type.PONG;

/**
 * This class provides a number of thread-safe {@link com.aerofs.daemon.transport.ITransport} and
 * {@link IUnicast} utility functions
 */
public class TPUtil
{
    private static final Logger l = Loggers.getLogger(TPUtil.class);

    /* unicast header
     *
     * +------------+----------
     * | PBTPHeader | payload (optional)
     * +------------+----------
     */

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
    public static PBTPHeader processUnicastPayload(Endpoint ep, PBTPHeader h, InputStream is,
            int wirelen, IBlockingPrioritizedEventSink<IEvent> sink, StreamManager sm)
        throws Exception
    {
        if (!h.hasStream()) {
            // Datagram
            sink.enqueueThrows(new EIUnicastMessage(ep, is, wirelen), Prio.LO);

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

            EIChunk event = alreadyBegun ? new EIChunk(ep, streamId, seq, is, wirelen)
                                         : new EIStreamBegun(ep, streamId, is, wirelen);
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

    private static PBTPHeader newAbortIncomingStreamHeader(StreamID streamId,
            InvalidationReason reason)
    {
        return PBTPHeader.newBuilder()
                .setType(STREAM)
                .setStream(PBStream.newBuilder()
                        .setType(Type.RX_ABORT_STREAM)
                        .setStreamId(streamId.getInt())
                        .setReason(reason))
                .build();
    }

    //
    // FIXME: attempt to polymorphise this set of control-processing methods!
    //

    /**
     * Process a control message that came in on a unicast channel. Control message
     * <strong>MUST NOT</strong> have type <code>PAYLOAD</code> or <code>DIAGNOSIS</code>
     *
     * @param ep {@link Endpoint} that sent the control message
     * @param h {@link PBTPHeader} control message itself
     * @param sink queue the {@link com.aerofs.daemon.transport.ITransport} or {@link IConnectionServiceListener} uses
     * to communicate with the {@link com.aerofs.daemon.core.Core}
     * @param sm {@link StreamManager} the {@link com.aerofs.daemon.transport.ITransport} or
     * {@link IConnectionServiceListener} uses to manage streams
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

    public static PBTPHeader processStreamControl(Endpoint ep, PBStream wireStream,
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

    /**
     * Process a unicast {@link PBTPHeader} message of type <code>DIAGNOSIS</code>
     *
     * @param did {@link com.aerofs.base.id.DID} of the peer that sent the diagnostic message
     * @param dg {@link PBTransportDiagnosis} diagnostic message itself
     * @param pd {@link IPipeDebug} instance of the debugging interface this method
     * can use to get basic diagnostic statistics (for example,
     * <code>getBytesReceived()</code> for <code>FLOOD_STAT_CALL</code>) about the peer
     * @param tds {@link TransportDiagnosisState} instance used to store
     * {@link FloodEntry} objects for the peer
     * @return a response {@link PBTransportDiagnosis} if necessary. <strong>IMPORTANT:</strong>
     * return value can be <code>null</code>
     * @throws ExProtocolError if the diagnosis message has an unrecognized type
     */
    public static PBTransportDiagnosis processUnicastControlDiagnosis(DID did,
            PBTransportDiagnosis dg, IPipeDebug pd, TransportDiagnosisState tds)
        throws ExProtocolError
    {
        switch (dg.getType()) {
        case PING:
            return PBTransportDiagnosis.newBuilder()
                .setType(PONG)
                .setPing(PBPing.newBuilder().setPingId(dg.getPing().getPingId()))
                .build();

        case PONG:
        {
            Long l = tds.getPing(dg.getPing().getPingId());
            if (l != null && l < 0) {
                long rtt = System.currentTimeMillis() + l;
                tds.putPing(dg.getPing().getPingId(), rtt);
            }
            break;
        }

        case FLOOD_DISCARD:
            break;

        case FLOOD_STAT_CALL:
        {
            long bytesrx = pd.getBytesReceived(did);
            long now = System.currentTimeMillis();
            int seq = dg.getFloodStatCall().getSeq();

            tds.putFlood(seq, new FloodEntry(now, bytesrx));

            return PBTransportDiagnosis.newBuilder()
                .setType(FLOOD_STAT_REPLY)
                .setFloodStatReply(PBFloodStatReply.newBuilder()
                        .setSeq(seq)
                        .setTime(now)
                        .setBytes(bytesrx))
                .build();
        }

        case FLOOD_STAT_REPLY:
            PBFloodStatReply reply = dg.getFloodStatReply();
            tds.putFlood(reply.getSeq(), new FloodEntry(reply.getTime(),
                reply.getBytes()));
            break;

        default:
            throw new ExProtocolError(PBTPHeader.Type.class);
        }

        return null;
    }

    /**
     * Construct a {@link PBTPHeader} message of type <code>DIAGNOSIS</code>
     * containing a valid <code>PBDiagnosis</code>
     *
     * @param dg {@link PBTransportDiagnosis} to embed as the payload of the constructed
     * {@link PBTPHeader} message. <strong>IMPORTANT:</strong> <code>dg</code>
     * <strong>MUST NOT</strong> be <code>null</code>
     * @return valid {@link PBTPHeader} of type <code>DIAGNOSIS</code> with
     * embedded <code>dg</code>
     */
    public static PBTPHeader makeDiagnosis(@Nonnull PBTransportDiagnosis dg)
    {
        return PBTPHeader.newBuilder().setType(DIAGNOSIS).setDiagnosis(dg).build();
    }

    public static void registerCommonHandlers(ITransportImpl tp)
    {
        tp.disp()
            .setHandler_(EOUnicastMessage.class, new HdUnicastMessage(tp))
            .setHandler_(EOBeginStream.class, new HdBeginStream(tp))
            .setHandler_(EOChunk.class, new HdChunk(tp))
            .setHandler_(EOTxEndStream.class, new HdTxEndStream(tp))
            .setHandler_(EORxEndStream.class, new HdRxEndStream(tp))
            .setHandler_(EOTxAbortStream.class, new HdTxAbortStream(tp))
            .setHandler_(EOTransportPing.class, new HdTransportPing(tp))
            .setHandler_(EOTransportFlood.class, new HdTransportFlood(tp))
            .setHandler_(EOTransportFloodQuery.class, new HdTransportFloodQuery(tp))
            .setHandler_(EOUpdateStores.class, new HdUpdateStores(tp))
            .setHandler_(EOLinkStateChanged.class, new HdLinkStateChanged(tp))
            .setHandler_(EOTpSubsequentPulse.class, new HdPulse<EOTpSubsequentPulse>(new SubsequentPulse(tp)))
            .setHandler_(EOStartPulse.class, new HdStartPulse(tp))
            .setHandler_(EOTpStartPulse.class, tp.sph())
            .setHandler_(EOTpSubsequentPulse.class, new HdPulse<EOTpSubsequentPulse>(new SubsequentPulse(tp)));
    }

    public static void registerMulticastHandler(ITransportImpl tp)
    {
        tp.disp().setHandler_(EOMaxcastMessage.class, new HdMaxcastMessage(tp));
    }

    public static void sessionEnded(Endpoint ep, IBlockingPrioritizedEventSink<IEvent> sink,
            StreamManager sm, boolean outbound, boolean inbound)
    {
        l.debug("closing streams " + ep + " out " + outbound + " in " + inbound);

        if (outbound) {
            sm.removeAllOutgoingStreams(ep.did());
            // don't signal stream abortion here because we expect the
            // transport to return errors for all outgoing chunks which in turn
            // forces the core to abort these streams.
        }

        try {
            if (inbound) {
                for (StreamID sid : sm.removeAllIncomingStreams(ep.did())) {
                    l.info("remove instrm " + ep.did() + ":" + sid);
                    sink.enqueueThrows(new EIStreamAborted(ep, sid, STREAM_NOT_FOUND), Prio.LO);
                }
            }

            sink.enqueueThrows(new EISessionEnded(ep, outbound, inbound), Prio.LO);
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
