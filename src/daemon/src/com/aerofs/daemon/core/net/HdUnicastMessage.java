package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

/**
 * Handler for the {@link EIUnicastMessage} event
 */
public class HdUnicastMessage implements IEventHandler<EIUnicastMessage>
{
    private static final Logger l = Util.l(HdUnicastMessage.class);

    private final FrequentDefectSender _fds = new FrequentDefectSender();
    private final UnicastInputOutputStack _stack;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdUnicastMessage(UnicastInputOutputStack stack, IMapSID2SIndex sid2sidx)
    {
        _stack = stack;
        _sid2sidx = sid2sidx;
    }

    @Override
    public void handle_(EIUnicastMessage ev, Prio prio)
    {
        SIndex sidx = _sid2sidx.getNullable_(ev._sid);
        if (sidx == null) {
            l.debug("no store " + ev._sid);

            //
            // we send a defect because in this case the message will be silently ignored,
            // causing the remote peer to timeout and start pulsing
            //

            _fds.logSendAsync("no local sidx for incoming uc sid:" + ev._sid);
        } else {
            PeerContext pc = new PeerContext(ev._ep, sidx);
            RawMessage r = new RawMessage(ev.is(), ev.wireLength());
            _stack.input().onUnicastDatagramReceived_(r, pc);
        }
    }
}
