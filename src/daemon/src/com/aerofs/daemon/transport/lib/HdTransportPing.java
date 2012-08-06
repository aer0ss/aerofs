package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.net.EOTransportPing;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.C;
import com.aerofs.proto.Transport.PBTransportDiagnosis;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;

public class HdTransportPing extends AbstractHdIMC<EOTransportPing>
{

    private final TransportDiagnosisState _tds;
    private final IUnicast _u;

    public HdTransportPing(ITransportImpl tp)
    {
        _tds = tp.tds();
        _u = tp.ucast();
    }

    @Override
    protected void handleThrows_(EOTransportPing ev, Prio prio) throws Exception
    {
        Long l = _tds.getPing(ev._seqPrev);

        if (l == null || l >= 0 || ev._forceNext) {

            PBTPHeader h = PBTPHeader.newBuilder()
                .setType(Type.DIAGNOSIS)
                .setDiagnosis(PBTransportDiagnosis.newBuilder()
                        .setType(PBTransportDiagnosis.Type.PING)
                        .setPing(PBTransportDiagnosis.PBPing.newBuilder()
                                .setSeq(ev._seqNext)))
                .build();

            _u.send_(ev._did, null, prio, TPUtil.newControl(h), null);

            if (l != null) _tds.removePing(ev._seqPrev);

            // the initial value is -(current time)
            _tds.putPing(ev._seqNext, -System.currentTimeMillis());
        }

        ev.setResult_(l != null && l < 0 ? (Long)
                C.TRANSPORT_DIAGNOSIS_STATE_PENDING : l);
    }
}
