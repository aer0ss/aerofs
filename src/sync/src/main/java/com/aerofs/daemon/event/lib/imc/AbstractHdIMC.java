package com.aerofs.daemon.event.lib.imc;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import org.slf4j.Logger;

import com.aerofs.daemon.event.IEBIMC;
import com.aerofs.daemon.event.IEventHandler;

public abstract class AbstractHdIMC<E extends IEBIMC> implements IEventHandler<E>
{
    protected static final Logger l = Loggers.getLogger(AbstractHdIMC.class);

    @Override
    public final void handle_(E ev)
    {
        try {
            handleThrows_(ev);
            ev.okay();
        } catch (Exception e) {
            l.warn("{} failed: ", getClass(), BaseLogUtil.suppress(e, ExNotFound.class));
            ev.error(e);
        }
    }

    protected abstract void handleThrows_(E ev) throws Exception;
}
