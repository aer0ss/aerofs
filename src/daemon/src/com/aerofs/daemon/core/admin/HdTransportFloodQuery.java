package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.admin.EITransportFloodQuery;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.net.EOTransportFloodQuery;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.ITransport;
import com.google.inject.Inject;

public class HdTransportFloodQuery extends AbstractHdIMC<EITransportFloodQuery>
{

    private final DevicePresence _dp;
    private final TC _tc;
    private final Transports _tps;

    @Inject
    public HdTransportFloodQuery(Transports tps, TC tc, DevicePresence dp)
    {
        _tps = tps;
        _tc = tc;
        _dp = dp;
    }

    @Override
    protected void handleThrows_(EITransportFloodQuery ev, Prio prio) throws Exception
    {
        ITransport tp = _dp.getOPMDeviceThrows_(ev.did())
                .getPreferedTransport_();

        EOTransportFloodQuery ev2 = new EOTransportFloodQuery(ev.seq(), _tps.getIMCE_(tp));
        CoreIMC.execute_(ev2, _tc, Cat.UNLIMITED);
        ev.setResult_(ev2._time, ev2._bytes);
    }
}
