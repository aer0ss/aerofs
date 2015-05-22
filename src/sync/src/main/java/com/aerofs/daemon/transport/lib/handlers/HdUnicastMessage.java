package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.transport.lib.IUnicast;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.log.LogUtil;
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
            byte[][] payload = TransportProtocolUtil.newDatagramPayload(ev.byteArray());
            unicast.send(ev._to, payload, ev.getWaiter());
        } catch (Exception e) {
            l.warn("{} fail send uc", ev._to, LogUtil.suppress(e, ExDeviceOffline.class, XMPPException.class));

            if (ev.getWaiter() != null) {
                ev.getWaiter().error(e);
            }
        }
    }
}
