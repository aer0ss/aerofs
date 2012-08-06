package com.aerofs.daemon.core.net.rx;

import com.aerofs.daemon.core.UnicastInputOutputStack;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

/**
 * Handler for a {@link EIMaxcastMessage}
 */
public class HdMaxcastMessage implements IEventHandler<EIMaxcastMessage>
{
    private final UnicastInputOutputStack _stack;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdMaxcastMessage(UnicastInputOutputStack stack, IMapSID2SIndex sid2sidx)
    {
        _sid2sidx = sid2sidx;
        _stack = stack;
    }

    @Override
    public void handle_(EIMaxcastMessage ev, Prio prio)
    {
        SIndex sidx = _sid2sidx.getNullable_(ev._sid);
        if (sidx == null) {
            Util.l(this).info("no store " + ev._sid);
        } else {
            try {
                _stack.inputTop().maxcastMessageReceived_(sidx, ev._ep, ev.is());
            } catch (Exception e) {
                Util.l(this).warn("process mc: " + Util.e(e,
                        ExDeviceOffline.class, ExBadCredential.class));
            }
        }
    }
}
