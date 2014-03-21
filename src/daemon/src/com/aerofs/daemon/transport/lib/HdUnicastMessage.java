package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import org.jivesoftware.smack.XMPPException;
import org.slf4j.Logger;

public class HdUnicastMessage implements IEventHandler<EOUnicastMessage>
{
    private static final Logger l = Loggers.getLogger(HdUnicastMessage.class);

    private final IUnicast unicast;

    public HdUnicastMessage(IUnicast unicast)
    {
        this.unicast = unicast;
    }

    @Override
    public void handle_(EOUnicastMessage ev, Prio prio)
    {
        try {
            byte[][] payload = TPUtil.newPayload(null, 0, ev.byteArray());
            unicast.send(ev._to, ev.getWaiter(), prio, payload, null);
        } catch (Exception e) {
            l.warn("uc " + ev._to + ": " + Util.e(e, ExDeviceOffline.class, XMPPException.class));

            if (ev.getWaiter() != null) {
                ev.getWaiter().error(e);
            }
        }
    }
}
