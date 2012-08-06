package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.device.Device;
import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.CoreIMC;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.admin.EITransportPing;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.event.net.EOTransportPing;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.C;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.google.inject.Inject;

public class HdTransportPing extends AbstractHdIMC<EITransportPing>
{
    private final DevicePresence _dp;
    private final Transports _tps;
    private final TC _tc;

    @Inject
    public HdTransportPing(TC tc, Transports tps, DevicePresence dp)
    {
        _tc = tc;
        _tps = tps;
        _dp = dp;
    }

    @Override
    protected void handleThrows_(EITransportPing ev, Prio prio) throws Exception
    {
        Device dev = _dp.getOPMDevice_(ev.did());
        if (dev == null && !ev.ignoreOffline()) throw new ExDeviceOffline();

        if (dev != null) {
            ev.setResult_(exec(ev, dev.getPreferedTransport_()));

        } else {
            final long FORGOTTEN = Long.MAX_VALUE;
            final long PENDING = FORGOTTEN - 1;
            long min = FORGOTTEN;
            for (ITransport tp : _tps.getAll_()) {
                Long rtt;
                try {
                    rtt = exec(ev, tp);
                } catch (ExDeviceOffline e) {
                    assert ev.ignoreOffline();
                    rtt = PENDING;
                }
                if (rtt == null) rtt = FORGOTTEN;
                else if (rtt == C.TRANSPORT_DIAGNOSIS_STATE_PENDING) rtt = PENDING;
                min = Math.min(rtt, min);
            }
            if (min == FORGOTTEN) ev.setResult_(null);
            else if (min == PENDING) ev.setResult_(C.TRANSPORT_DIAGNOSIS_STATE_PENDING);
            else ev.setResult_(min);
        }
    }

    private Long exec(EITransportPing ev, ITransport tp) throws Exception
    {
        EOTransportPing ev2 = new EOTransportPing(ev.did(), ev.seqPrev(),
                ev.seqNext(), ev.forceNext(), _tps.getIMCE_(tp));
        CoreIMC.execute_(ev2, _tc, Cat.UNLIMITED);
        return ev2._rtt;
    }
}
