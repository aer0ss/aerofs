package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.tx.EOMaxcastMessage;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.Prio;
import org.jivesoftware.smack.XMPPException;
import org.slf4j.Logger;

import java.io.IOException;

public class HdMaxcastMessage implements IEventHandler<EOMaxcastMessage>
{
    private static final Logger l = Loggers.getLogger(HdMaxcastMessage.class);

    private final IMaxcast maxcast;

    public HdMaxcastMessage(IMaxcast maxcast)
    {
        this.maxcast = maxcast;
    }

    @Override
    public void handle_(EOMaxcastMessage ev, Prio prio)
    {
        try {
            maxcast.sendPayload(ev._sid, ev._mcastid, ev.byteArray());
        } catch (XMPPException e) {
            l.warn("mc " + ev._sid + " " + ev._mcastid + ": " + e.getMessage());
        } catch (Exception e) {
            l.warn("mc " + ev._sid + " " + ev._mcastid + ": " + Util.e(e, IOException.class));
        }
    }
}
