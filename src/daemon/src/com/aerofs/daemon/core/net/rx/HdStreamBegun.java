package com.aerofs.daemon.core.net.rx;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.net.PeerContext;
import com.aerofs.daemon.core.net.RawMessage;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

/**
 * Handler for the {@link EIStreamBegun} event
 */
public class HdStreamBegun implements IEventHandler<EIStreamBegun>
{
    private static final Logger l = Util.l(HdStreamBegun.class);
    private final UnicastInputOutputStack _stack;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdStreamBegun(UnicastInputOutputStack stack, IMapSID2SIndex sid2sidx)
    {
        _stack = stack;
        _sid2sidx = sid2sidx;
    }

    @Override
    public void handle_(EIStreamBegun ev, Prio prio)
    {
        SIndex sidx = _sid2sidx.getNullable_(ev._sid);
        if (sidx == null) {
            l.info("no store " + ev._sid);
            return;
        } else {
            PeerContext pc = new PeerContext(ev._ep, sidx);
            RawMessage r = new RawMessage(ev.is(), ev.wireLength());
            _stack.input().onStreamBegun_(ev._streamId, r, pc);
        }
    }

}
