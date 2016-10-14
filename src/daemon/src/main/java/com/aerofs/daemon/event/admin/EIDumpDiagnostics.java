/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.proto.Ritual.GetDiagnosticsReply;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class EIDumpDiagnostics extends AbstractEBIMC
{
    private GetDiagnosticsReply _diagnostics;

    public EIDumpDiagnostics(IIMCExecutor imce)
    {
        super(imce);
    }

    public void setResult_(GetDiagnosticsReply diagnostics)
    {
        checkState(_diagnostics == null, "diagnostics already set prev:" + _diagnostics);

        _diagnostics = diagnostics;
    }

    public GetDiagnosticsReply getDiagnostics_()
    {
        return checkNotNull(_diagnostics);
    }
}
