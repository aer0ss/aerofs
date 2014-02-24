package com.aerofs.daemon.event.lib.imc;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import com.aerofs.daemon.event.IEBIMC;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.lib.event.Prio;

public abstract class AbstractHdIMC<E extends IEBIMC> implements IEventHandler<E>
{
    protected static final Logger l = Loggers.getLogger(AbstractHdIMC.class);

    @Override
    public final void handle_(E ev, Prio prio)
    {
        try {
            handleThrows_(ev, prio);
            ev.okay();
        } catch (Exception e) {
            l.warn("{} failed: ", getClass(), e);
            ev.error(e);
        }
    }

    protected abstract void handleThrows_(E ev, Prio prio) throws Exception;
}
