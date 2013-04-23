package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.admin.EITransportFlood;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.net.EOTransportFlood;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBCoreDiagnosis;
import com.aerofs.proto.Core.PBCoreDiagnosis.PBRequestTransportFlood;
import com.google.inject.Inject;

public class HdTransportFlood extends AbstractHdIMC<EITransportFlood>
{
    private final DevicePresence _dp;
    private final NSL _nsl;
    private final Transports _tps;
    private final TC _tc;

    @Inject
    public HdTransportFlood(TC tc, Transports tps, NSL nsl,
            DevicePresence dp)
    {
        _tc = tc;
        _tps = tps;
        _nsl = nsl;
        _dp = dp;
    }

    @Override
    protected void handleThrows_(EITransportFlood ev, Prio prio) throws Exception
    {
        ITransport tp = _dp.getOPMDeviceThrows_(ev._did)
            .getPreferedTransport_();

        if (!ev._send) {

            // request the peer to flood us. core-layer messaging instead of
            // transport layer is used to prevent DoS attacks

            if (ev._sname == null) {
                throw new ExBadArgs("sname mustn't be null for receiving");
            }

            assert false;
            SIndex sidx = null; //c.sdm().getStoreByNameThrows_(ev._sname);

            PBCore core = CoreUtil.newCall(Type.DIAGNOSIS)
                .setDiagnosis(PBCoreDiagnosis.newBuilder()
                        .setType(PBCoreDiagnosis.Type.REQUEST_TRANSPORT_FLOOD)
                        .setRequestTransportFlood(PBRequestTransportFlood.newBuilder()
                                .setDuration(ev._duration)
                                .setSeqStart(ev._seqStart)
                                .setSeqEnd(ev._seqEnd)))
                .build();

            _nsl.sendUnicast_(new Endpoint(tp, ev._did), sidx, core);
        }

        EOTransportFlood ev2 = new EOTransportFlood(ev._did, ev._send,
                ev._seqStart, ev._seqEnd, ev._duration, _tps.getIMCE_(tp));
        CoreIMC.execute_(ev2, _tc, Cat.UNLIMITED);
    }
}
