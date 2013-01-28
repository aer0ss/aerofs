package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

/**
 * Handler for a {@link EIMaxcastMessage}
 */
public class HdMaxcastMessage implements IEventHandler<EIMaxcastMessage>
{
    private static final Logger l = Util.l(HdMaxcastMessage.class);

    private final UnicastInputOutputStack _stack;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdMaxcastMessage(UnicastInputOutputStack stack, IMapSID2SIndex sid2sidx)
    {
        _stack = stack;
        _sid2sidx = sid2sidx;
    }

    @Override
    public void handle_(EIMaxcastMessage ev, Prio prio)
    {
        SIndex sidx = _sid2sidx.getNullable_(ev._sid);
        if (sidx == null) {
            l.debug("no store " + ev._sid);
        } else {
            _stack.inputTop().maxcastMessageReceived_(sidx, ev._ep, ev.is());
        }
    }
}
