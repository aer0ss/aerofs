package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.SOID;

// FSI shall fire this event right before the first write on a component.
// The event handler will call IWriteSession.preWrite() once.
//
public class EIBeginWrite extends AbstractEIFS
{

    public final SOID _soid;
    public IWriteSession _ws;

    public EIBeginWrite(String user, SOID soid, IIMCExecutor imce)
    {
        super(user, imce);
        _soid = soid;
    }

    public void setResult_(IWriteSession ws)
    {
        _ws = ws;
    }
}
