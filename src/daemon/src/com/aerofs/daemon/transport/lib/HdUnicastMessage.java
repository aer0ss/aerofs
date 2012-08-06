package com.aerofs.daemon.transport.lib;

import org.jivesoftware.smack.XMPPException;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExDeviceOffline;

public class HdUnicastMessage implements IEventHandler<EOUnicastMessage> {

    private final IUnicast _ucast;

    public HdUnicastMessage(ITransportImpl tp)
    {
        _ucast = tp.ucast();
    }

    @Override
    public void handle_(EOUnicastMessage ev, Prio prio)
    {
        try {
            _ucast.send_(ev._to, null, prio, TPUtil.newPayload(null, 0, ev._sid, ev.byteArray()),
                    null);
        } catch (Exception e) {
            Util.l(this).warn("uc " + ev._to +  ": " + Util.e(e,
                    ExDeviceOffline.class, XMPPException.class));
        }
    }

}
