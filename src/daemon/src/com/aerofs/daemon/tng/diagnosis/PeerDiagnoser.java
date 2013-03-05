/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.diagnosis;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.tng.IPeerDiagnoser;
import com.aerofs.daemon.tng.base.IUnicastConnection;
import com.aerofs.daemon.tng.diagnosis.TransportDiagnosisState.FloodEntry;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTransportDiagnosis;
import com.aerofs.proto.Transport.PBTransportDiagnosis.PBFloodStatReply;
import com.aerofs.proto.Transport.PBTransportDiagnosis.PBPong;
import com.google.common.util.concurrent.ListenableFuture;

import static com.aerofs.proto.Transport.PBTPHeader.Type.DIAGNOSIS;
import static com.aerofs.proto.Transport.PBTransportDiagnosis.Type.FLOOD_STAT_REPLY;
import static com.aerofs.proto.Transport.PBTransportDiagnosis.Type.PONG;

/**
 */
public class PeerDiagnoser implements IPeerDiagnoser
{
    @Override
    public ListenableFuture<Void> processDiagnosisPacket(DID did)
    {
        // FIXME: fix fix! Think fo the TDS story
//    /**
//     * Process an incoming {@link com.aerofs.proto.AbstractTransport.PBDiagnosis} diagnostic control message from a
//     * peer inside the <code>XMPPBasedTransportFactory</code> event-dispatch thread
//     * <br/>
//     * <br/>
//     * <strong>IMPORTANT:</strong> asserts that this method is <em>only</em>
//     * called from within the <code>XMPPBasedTransportFactory</code> event-dispatch thread
//     *
//     * @param _did {@link com.aerofs.base.id.DID} of the peer that sent the diagnostic message
//     * @param dg {@link com.aerofs.proto.AbstractTransport.PBDiagnosis} diagnostic message sent by the peer
//     * @throws com.aerofs.lib.ex.ExProtocol if the diagnostic message has a type that is unrecognized
//     * (and therefore unprocessable) by this method
//     */
//    private void processDiagnosis_(DID _did, PBDiagnosis dg)
//        throws ExProtocol
//    {
//        PBDiagnosis dgret = processUnicastControlDiagnosis(_did, dg, _spf, _tds);
//        if (dgret != null) {
//            PBTPHeader ret = makeDiagnosis(dgret);
//            sendControl_(_did, ret, LO);
//        }
//    }

        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public static PBTransportDiagnosis processUnicastControlDiagnosis(DID did,
            PBTransportDiagnosis dg, IUnicastConnection conn, TransportDiagnosisState tds)
            throws ExProtocolError
    {
        switch (dg.getType()) {
        case PING:
            return PBTransportDiagnosis.newBuilder()
                    .setType(PONG)
                    .setPong(PBPong.newBuilder().setSeq(dg.getPing().getSeq()))
                    .build();

        case PONG: {
            Long l = tds.getPing(dg.getPong().getSeq());
            if (l != null && l < 0) {
                long rtt = System.currentTimeMillis() + l;
                tds.putPing(dg.getPong().getSeq(), rtt);
            }
            break;
        }

        case FLOOD_DISCARD:
            break;

        case FLOOD_STAT_CALL: {
            long bytesrx = 0; // pd.getBytesRx(did); // FIXME: this is wrong!!!
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
            tds.putFlood(reply.getSeq(), new FloodEntry(reply.getTime(), reply.getBytes()));
            break;

        default:
            throw new ExProtocolError(PBTPHeader.Type.class);
        }

        return null;
    }

    /**
     * Construct a {@link PBTPHeader} message of type <code>DIAGNOSIS</code> containing a valid
     * <code>PBDiagnosis</code>
     *
     * @param dg {@link PBTransportDiagnosis} to embed as the payload of the constructed {@link
     * PBTPHeader} message. <strong>IMPORTANT:</strong> <code>dg</code> <strong>MUST NOT</strong> be
     * <code>null</code>
     * @return valid {@link PBTPHeader} of type <code>DIAGNOSIS</code> with embedded
     *         <code>dg</code>
     */
    public static PBTPHeader makeDiagnosis(PBTransportDiagnosis dg)
    {
        return PBTPHeader.newBuilder().setType(DIAGNOSIS).setDiagnosis(dg).build();
    }

    // private final TransportDiagnosisState _tds = new TransportDiagnosisState();
}
