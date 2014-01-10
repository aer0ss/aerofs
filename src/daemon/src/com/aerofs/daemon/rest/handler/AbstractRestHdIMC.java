/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.handler;

import com.aerofs.daemon.core.activity.ActivityLog;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.AbstractRestEBIMC;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.lib.event.Prio;

public abstract class AbstractRestHdIMC<T extends AbstractRestEBIMC> extends AbstractHdIMC<T>
{
    protected final TransManager _tm;
    protected final RestObjectResolver _access;
    protected final MetadataBuilder _mb;
    protected final EntityTagUtil _etags;

    protected AbstractRestHdIMC(RestObjectResolver access, EntityTagUtil etags, MetadataBuilder mb,
            TransManager tm)
    {
        _tm = tm;
        _access = access;
        _mb = mb;
        _etags = etags;
    }

    @Override
    protected final void handleThrows_(T ev, Prio prio) throws Exception
    {
        try {
            ActivityLog.onBehalfOf(ev._did);
            handleThrows_(ev);
        } finally {
            ActivityLog.onBehalfOf(null);
        }
    }

    protected abstract void handleThrows_(T ev) throws Exception;
}
