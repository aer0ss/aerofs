package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOMaxcastMessage;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.Util;
import org.jivesoftware.smack.XMPPException;
import org.slf4j.Logger;

import java.io.IOException;

public class HdMaxcastMessage implements IEventHandler<EOMaxcastMessage>
{
    private static final Logger l = Loggers.getLogger(HdMaxcastMessage.class);

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
            l.warn("mc " + ev._sid + " " + ev._mcastid + ": " + e.getMessage());
        } catch (Exception e) {
            l.warn("mc " + ev._sid + " " + ev._mcastid + ": " + Util.e(e, IOException.class));
        }
    }
}
