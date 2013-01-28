package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.core.linker.event.EIMightDeleteNotification;
import com.aerofs.daemon.core.linker.scanner.ScanSessionQueue;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.Util;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.util.Collections;

class HdMightDeleteNotification implements IEventHandler<EIMightDeleteNotification>
{
    private static final Logger l = Util.l(HdMightCreateNotification.class);
    private final MightDelete _md;
    private final ScanSessionQueue _ssq;
    private final IDeletionBuffer _globalBuffer;
    private final CfgAbsRootAnchor _cfgAbsRootAnchor;

    @Inject
    HdMightDeleteNotification(MightDelete md,
            ScanSessionQueue ssq,
            IDeletionBuffer globalBuffer,
            CfgAbsRootAnchor cfgAbsRootAnchor)
    {
        _md = md;
        _ssq = ssq;
        _globalBuffer = globalBuffer;
        _cfgAbsRootAnchor = cfgAbsRootAnchor;
    }

    @Override
    public void handle_(EIMightDeleteNotification ev, Prio prio)
    {
        try {
            _md.mightDelete_(new PathCombo(_cfgAbsRootAnchor, ev._absPath), _globalBuffer);
        } catch (Exception e) {
            // On any exception, perform a full scan
            l.warn("full scan triggered by " + Util.e(e));
            _ssq.scanAfterDelay_(Collections.singleton(_cfgAbsRootAnchor.get()), true);
        }
    }

}
