package com.aerofs.daemon.core;

import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.event.IEBIMC;
import com.aerofs.lib.event.IEvent;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CoreEventDispatcher extends EventDispatcher
{
    private volatile long _executedEventCount;

    private ConcurrentHashMap<TCB, IEvent> _evCurrent = new ConcurrentHashMap<>();

    @Inject
    public CoreEventDispatcher(Set<ICoreEventHandlerRegistrar> registrars)
    {
        for (ICoreEventHandlerRegistrar registrar : registrars) registrar.registerHandlers_(this);

        // Use a default handler to notify the end user about unsupported functions that are
        // otherwise implemented in subsystems that are not activated in this particular
        // distribution. Since UI in different distributions should have been specialized to avoid
        // calling unsupported functions, this default handler is used mainly for users accessing
        // the Ritual API.
        //
        // This default handler only accepts events derived from IEBIMC. Other event types would
        // trigger runtime casting errors.
        //
        setDefaultHandler_(new AbstractHdIMC<IEBIMC>()
        {
            @Override
            protected void handleThrows_(IEBIMC ev)
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

        final TCB tcb = TC.tcb();
        if (tcb != null)_evCurrent.put(tcb, ev);
        try {
            super.dispatch_(ev, prio);
        } finally {
            if (tcb != null) _evCurrent.remove(tcb);
        }
    }

    /**
     * @return events being executed on active core threads
     */
    public Map<TCB, IEvent> getCurrentEvents()
    {
        return _evCurrent;
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
