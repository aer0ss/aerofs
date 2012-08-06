package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ComMonitor;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.fs.EIEndRead;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCKID;
import com.google.inject.Inject;

public class HdEndRead implements IEventHandler<EIEndRead> {

    private final ComMonitor _cm;

    @Inject
    public HdEndRead(ComMonitor cm)
    {
        _cm = cm;
    }

    @Override
    public void handle_(EIEndRead ev, Prio prio)
    {
        _cm.endRead_(new SOCKID(ev.sokid(), CID.CONTENT));
    }

}
