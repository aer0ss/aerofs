package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ComMonitor;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.event.fs.EIBeginWrite;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Role;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCKID;
import com.google.inject.Inject;

public class HdBeginWrite extends AbstractHdIMC<EIBeginWrite>
{
    private final LocalACL _lacl;
    private final ComMonitor _cm;

    @Inject
    public HdBeginWrite(ComMonitor cm, LocalACL lacl)
    {
        _cm = cm;
        _lacl = lacl;
    }

    @Override
    protected void handleThrows_(EIBeginWrite ev, Prio prio) throws Exception
    {
        _lacl.checkThrows_(ev.user(), ev._soid.sidx(), Role.EDITOR);

        SOCKID k = new SOCKID(ev._soid, CID.CONTENT);
        ev.setResult_(_cm.beginWrite_(k, null));
    }
}
