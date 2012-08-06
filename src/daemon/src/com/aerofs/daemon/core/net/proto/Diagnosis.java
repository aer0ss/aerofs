package com.aerofs.daemon.core.net.proto;

import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.net.EOTransportFlood;
import com.aerofs.lib.Util;
import com.aerofs.proto.Core.PBCoreDiagnosis.PBRequestTransportFlood;
import com.google.inject.Inject;

public class Diagnosis
{
    private final Transports _tps;
    private final TC _tc;

    @Inject
    public Diagnosis(TC tc, Transports tps)
    {
        _tc = tc;
        _tps = tps;
    }

    public void process_(DigestedMessage msg) throws Exception
    {
        Util.checkPB(msg.pb().getDiagnosis().hasRequestTransportFlood(),
                PBRequestTransportFlood.class);

        PBRequestTransportFlood r = msg.pb().getDiagnosis()
                .getRequestTransportFlood();

        EOTransportFlood ev = new EOTransportFlood(msg.did(), true,
                r.getSeqStart(), r.getSeqEnd(), r.getDuration(),
                _tps.getIMCE_(msg.tp()));
        CoreIMC.execute_(ev, _tc, Cat.UNLIMITED);
    }
}
