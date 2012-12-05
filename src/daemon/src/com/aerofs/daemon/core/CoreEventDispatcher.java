package com.aerofs.daemon.core;

import com.aerofs.daemon.event.IEBIMC;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;

import javax.inject.Inject;
import java.util.Set;

public class CoreEventDispatcher extends EventDispatcher
{
    private volatile long _executedEventCount;

    @Inject
    public CoreEventDispatcher(Set<ICoreEventHandlerRegistrar> registrars)
    {
        for (ICoreEventHandlerRegistrar registrar : registrars) registrar.registerHandlers_(this);

        // Use a default handler to notify the end user about unsupported functions that are
        // otherwise implemented in subsystems that are not activiated in this particular
        // distribution. Since UI in different distributions should have been specialized to avoid
        // calling unsupported functions, this default handler is used mianly for users accessing
        // the Ritual API.
        //
        // This default handler only accepts events derived from IEBIMC. Other event types would
        // trigger runtime casting errors.
        //
        setDefaultHandler_(new AbstractHdIMC<IEBIMC>()
        {
            @Override
            protected void handleThrows_(IEBIMC ev, Prio prio)
                    throws Exception
            {
                throw new ExNotSupported();
            }
        });
    }

    @Override
    public void dispatch_(IEvent ev, Prio prio)
    {
        _executedEventCount++;

        super.dispatch_(ev, prio);
    }

    public long getExecutedEventCount()
    {
        return _executedEventCount;
    }

    private static class ExNotSupported extends Exception
    {
        private static final long serialVersionUID = 0;

        ExNotSupported()
        {
            super("the function is not supported in this distribution");
        }
    }
}
