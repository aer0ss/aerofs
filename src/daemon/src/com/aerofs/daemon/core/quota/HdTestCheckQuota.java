/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.quota;

import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;

import javax.inject.Inject;

public class HdTestCheckQuota extends AbstractHdIMC<EITestCheckQuota>
{
    @Inject
    QuotaEnforcement _quotaEnforcement;

    @Override
    protected void handleThrows_(EITestCheckQuota ev, Prio prio)
            throws Exception
    {
        _quotaEnforcement.enforceQuotas_();
    }
}
