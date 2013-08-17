/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.proto.Ritual.GetTransportDiagnosticsReply;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class EITransportDiagnostics extends AbstractEBIMC
{
    private GetTransportDiagnosticsReply _diagnostics;

    public EITransportDiagnostics(IIMCExecutor imce)
    {
        super(imce);
    }

    public void setResult_(GetTransportDiagnosticsReply diagnostics)
    {
        checkState(_diagnostics == null, "diagnostics already set prev:" + _diagnostics);

        _diagnostics = diagnostics;
    }

    public GetTransportDiagnosticsReply getDiagnostics_()
    {
        return checkNotNull(_diagnostics);
    }
}
