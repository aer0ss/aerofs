package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOMaxcastMessage;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import org.jivesoftware.smack.XMPPException;

import java.io.IOException;

public class HdMaxcastMessage implements IEventHandler<EOMaxcastMessage>
{
    private final IMaxcast _mcast;

    public HdMaxcastMessage(ITransportImpl tp)
    {
        _mcast = tp.mcast();
    }

    @Override
    public void handle_(EOMaxcastMessage ev, Prio prio)
    {
        try {
            _mcast.sendPayload(ev._sid, ev._mcastid, ev.byteArray());
        } catch (XMPPException e) {
            Util.l(this).warn(
                  "mc " + ev._sid + " " + ev._mcastid + ": " + e.getMessage());
        } catch (Exception e) {
            Util.l(this).warn(
                  "mc " + ev._sid + " " + ev._mcastid + ": " + Util.e(e,
                    IOException.class));
        }
    }
}
