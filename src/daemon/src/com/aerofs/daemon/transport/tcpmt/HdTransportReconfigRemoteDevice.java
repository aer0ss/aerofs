package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EOTransportReconfigRemoteDevice;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExFormatError;

class HdTransportReconfigRemoteDevice
implements IEventHandler<EOTransportReconfigRemoteDevice> {

    private final TCP t;

    HdTransportReconfigRemoteDevice(TCP t)
    {
        this.t = t;
    }

    @Override
    public void handle_(EOTransportReconfigRemoteDevice ev, Prio prio)
    {
        assert !ev._did.equals(Cfg.did());

        TPUtil.HostAndPort hnp;
        try {
            hnp = new TPUtil.HostAndPort(ev._tcpEndpoint);
            t.hm().put(ev._did, hnp._host, hnp._port);
        } catch (ExFormatError e) {
            Util.l(this).warn(Util.e(e));
        }
    }
}
