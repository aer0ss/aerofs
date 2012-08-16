package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEvent;
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
import com.aerofs.daemon.lib.IBlockingPrioritizedEventSink;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.TransportDiagnosisState.FloodEntry;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBStream.Type;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTransportDiagnosis;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.PatternSyntaxException;

import static com.aerofs.proto.Transport.PBStream.InvalidationReason.STREAM_NOT_FOUND;
import static com.aerofs.proto.Transport.PBTPHeader.Type.DATAGRAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.DIAGNOSIS;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.aerofs.proto.Transport.PBTransportDiagnosis.PBFloodStatReply;
import static com.aerofs.proto.Transport.PBTransportDiagnosis.PBPong;
import static com.aerofs.proto.Transport.PBTransportDiagnosis.Type.FLOOD_STAT_REPLY;
import static com.aerofs.proto.Transport.PBTransportDiagnosis.Type.PONG;

/**
 * This class provides a number of thread-safe {@link ITransport} and
 * {@link IUnicast} utility functions
 */
public class TPUtil
{
    public static class HostAndPort
    {
        public final String _host;
        public final int _port;

        public HostAndPort(String endpoint) throws ExFormatError
        {
            try {
                String[] parts = endpoint.split(":");
                _host = parts[0];
                _port = Integer.valueOf(parts[1]);
            } catch (PatternSyntaxException e) {
                throw new ExFormatError(e.getMessage());
            }
        }
    }

    private static final Logger l = Util.l(TPUtil.class);

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
    public static byte[][] newPayload(StreamID streamId, int seq, SID sid, byte[] bs)
    {
        PBTPHeader.Builder bdHeader = PBTPHeader.newBuilder()
            .setType(DATAGRAM)
            .setSid(sid.toPB());

        if (streamId != null) {
            bdHeader.setType(STREAM)
                    .setStream(PBStream.newBuilder()
                            .setType(Type.PAYLOAD)
                            .setStreamId(streamId.getInt())
                            .setSeqNum(seq));
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
        };

        return new byte[][] {
            Util.writeDelimited(h).toByteArray(),
        };
    }

    public static PBTPHeader processUnicastHeader(ByteArrayInputStream is)
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
    public static PBTPHeader processUnicastPayload(
        Endpoint ep, PBTPHeader h, ByteArrayInputStream is, int wirelen,
        IBlockingPrioritizedEventSink<IEvent> sink, StreamManager sm)
        throws Exception
    {
        if (!h.hasStream()) {
            sink.enqueueThrows(new EIUnicastMessage(ep, new SID(h.getSid()), is, wirelen), Prio.LO);

        } else {
            PBStream wireStream = h.getStream();

            StreamID streamId = new StreamID(wireStream.getStreamId());

            assert wireStream.hasSeqNum();
            int seq = wireStream.getSeqNum();

            Boolean b = sm.getIncomingStream(ep.did(), streamId, seq);
            if (b == null) {
                l.info("stream " + ep.did() + ':' + streamId + " not found. send rx abort");
                return PBTPHeader.newBuilder()
                        .setType(STREAM)
                        .setStream(PBStream.newBuilder()
                                .setType(Type.RX_ABORT_STREAM)
                                .setStreamId(streamId.getInt())
                                .setReason(STREAM_NOT_FOUND))
                        .build();
            } else if (b) {
                try {
                    sink.enqueueThrows(new EIChunk(ep, new SID(h.getSid()), streamId, seq, is,
                            wirelen), Prio.LO);
                } catch (Exception e) {
                    l.info("can't enqueue EIChunk. abort stream: " + Util.e(e));
                    sm.removeIncomingStream(ep.did(), streamId);
                    throw e;
                }
            } else {
                try {
                    sink.enqueueThrows(new EIStreamBegun(ep, new SID(h.getSid()), streamId, is,
                            wirelen), Prio.LO);
                } catch (Exception e) {
                    l.info("can't enqueue EIStreamBegun. abort stream: " + Util.e(e));
                    sm.removeIncomingStream(ep.did(), streamId);
                    throw e;
                }
            }
        }

        return null;
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
     * @param sink queue the {@link ITransport} or {@link IPipeController} uses
     * to communicate with the {@link com.aerofs.daemon.core.Core}
     * @param sm {@link StreamManager} the {@link ITransport} or {@link IPipeController}
     * uses to manage streams
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
            sm.newIncomingStream(ep.did(), streamId);
            break;
        case TX_ABORT_STREAM:
            l.info("stream " + streamId + " tx aborted: " + wireStream.getReason());
            sm.removeIncomingStream(ep.did(), streamId);
            // it must be the last statement of the case block as it may throw
            sink.enqueueThrows(new EIStreamAborted(ep, streamId, wireStream.getReason()), Prio.LO);
            break;
        case RX_ABORT_STREAM:
            l.info("stream " + streamId + " rx aborted: " + wireStream.getReason());
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
     * @param did {@link DID} of the peer that sent the diagnostic message
     * @param dg {@link PBTransportDiagnosis} diagnostic message itself
     * @param pd {@link IPipeDebug} instance of the debugging interface this method
     * can use to get basic diagnostic statistics (for example,
     * <code>getBytesRx()</code> for <code>FLOOD_STAT_CALL</code>) about the peer
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
                .setPong(PBPong.newBuilder()
                        .setSeq(dg.getPing().getSeq()))
                .build();

        case PONG:
        {
            Long l = tds.getPing(dg.getPong().getSeq());
            if (l != null && l < 0) {
                long rtt = System.currentTimeMillis() + l;
                tds.putPing(dg.getPong().getSeq(), rtt);
            }
            break;
        }

        case FLOOD_DISCARD:
            break;

        case FLOOD_STAT_CALL:
        {
            long bytesrx = pd.getBytesRx(did);
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
            .setHandler_(EOMaxcastMessage.class, new HdMaxcastMessage(tp))
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

    public static void sessionEnded(Endpoint ep, IBlockingPrioritizedEventSink<IEvent> sink,
            StreamManager sm, boolean outbound, boolean inbound)
    {
        l.info("session " + ep + " ended: out " + outbound + " in " + inbound);

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

        return (type == STREAM &&
                        transportHeader.getStream().getType() == Type.PAYLOAD) ||
                type == DATAGRAM;
    }
}
