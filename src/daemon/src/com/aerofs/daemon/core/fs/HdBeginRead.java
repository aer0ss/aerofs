package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ComMonitor;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.event.fs.EIBeginRead;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Role;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCKID;
import com.google.inject.Inject;

public class HdBeginRead extends AbstractHdIMC<EIBeginRead>
{
    private final LocalACL _lacl;
    private final ComMonitor _cm;

    @Inject
    public HdBeginRead(ComMonitor cm, LocalACL lacl)
    {
        _cm = cm;
        _lacl = lacl;
    }

    @Override
    protected void handleThrows_(EIBeginRead ev, Prio prio) throws Exception
    {
        _lacl.checkThrows_(ev.user(), ev._sokid.sidx(), Role.VIEWER);

        SOCKID k = new SOCKID(ev._sokid, CID.CONTENT);
        _cm.beginRead_(k);
    }
}
