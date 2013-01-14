package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.event.net.EOTransportFlood;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.lib.TransportDiagnosisState.FloodEntry;
import com.aerofs.lib.Param;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.proto.Transport.PBTransportDiagnosis;
import com.aerofs.proto.Transport.PBTransportDiagnosis.PBFloodStatCall;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class HdTransportFlood extends AbstractHdIMC<EOTransportFlood>
{

    private final TransportDiagnosisState _tds;
    private final IUnicast _u;

    public HdTransportFlood(ITransportImpl tp)
    {
        _tds = tp.tds();
        _u = tp.ucast();
    }

    @Override
    protected void handleThrows_(EOTransportFlood ev, Prio prio) throws Exception
    {
        _tds.putFlood(ev._seqStart, new FloodEntry(Param.TRANSPORT_DIAGNOSIS_STATE_PENDING, 0));
        _tds.putFlood(ev._seqEnd, new FloodEntry(Param.TRANSPORT_DIAGNOSIS_STATE_PENDING, 0));

        if (ev._send) {
            Flooder f = new Flooder(ev._did, ev._duration, ev._seqStart,
                    ev._seqEnd, prio);
            f.start();
        }
    }

    private class Flooder implements IResultWaiter {

        private final DID _did;
        private final Prio _prio;
        private final long _duration;
        private byte[][] _bssDiscard;
        private final int _seqStart, _seqEnd;
        private long _start;
        private boolean _end;

        Flooder(DID did, long duration, int seqStart, int seqEnd, Prio prio)
        {
            _did = did;
            _duration = duration;
            _prio = prio;
            _seqStart = seqStart;
            _seqEnd = seqEnd;

            // initialize payload data
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                PBTPHeader.newBuilder()
                    .setType(Type.DIAGNOSIS)
                    .setDiagnosis(PBTransportDiagnosis.newBuilder()
                            .setType(PBTransportDiagnosis.Type.FLOOD_DISCARD))
                    .build().writeDelimitedTo(os);
            } catch (IOException e) {
                SystemUtil.fatal(e);
            }

            byte[] padding = new byte[DaemonParam.TRANSPORT_FLOOD_MESSAGE_SIZE -
                                      os.size()];
            for (int i = 0; i < padding.length; i++) {
                padding[i] = (byte) ((int) (Math.random() * Integer.MAX_VALUE)
                        & 0xFF);
            }

            _bssDiscard = new byte[][] { os.toByteArray(), padding };
        }

        private byte[][] newFloodStat(int seq)
        {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                PBTPHeader.newBuilder()
                    .setType(Type.DIAGNOSIS)
                    .setDiagnosis(PBTransportDiagnosis.newBuilder()
                            .setType(PBTransportDiagnosis.Type.FLOOD_STAT_CALL)
                            .setFloodStatCall(PBFloodStatCall.newBuilder()
                                    .setSeq(seq)))
                    .build().writeDelimitedTo(os);
            } catch (IOException e) {
                SystemUtil.fatal(e);
            }

            return new byte[][] { os.toByteArray(), _bssDiscard[1] };
        }

        void start() throws Exception
        {
            _start = System.currentTimeMillis();
            sendNext(_seqStart);
        }

        /**
         * @param seq null to send DISCARD messages
         */
        private void sendNext(Integer seq) throws Exception
        {
            _u.send_(_did, this, _prio,
                seq == null ? _bssDiscard : newFloodStat(seq),
                null);
        }

        @Override
        public void okay()
        {
            if (_end) return;

            try {
                if (System.currentTimeMillis() - _start > _duration) {
                    _end = true;
                    sendNext(_seqEnd);
                } else {
                    sendNext(null);
                }
            } catch (Exception e) {
                error(e);
            }
        }

        @Override
        public void error(Exception e)
        {
            Util.l(this).warn("remove flood seq #: " + Util.e(e));
            _tds.removeFlood(_seqStart);
            _tds.removeFlood(_seqEnd);
        }

    }
}
