/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.ITransferStat;

public class EIGetTransferStat extends AbstractEBIMC
{
    public ITransferStat _ts;

    public EIGetTransferStat(IIMCExecutor imce)
    {
        super(imce);
    }

    void setResult(ITransferStat ts) { _ts = ts; }
}
