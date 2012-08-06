package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.core.linker.MightCreate.Result;
import com.aerofs.daemon.core.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.linker.scanner.ScanSessionQueue;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.util.Collections;

class HdMightCreateNotification implements IEventHandler<EIMightCreateNotification>
{
    private static final Logger l = Util.l(HdMightCreateNotification.class);
    private final MightCreate _mc;
    private final ScanSessionQueue _ssq;
    private final IDeletionBuffer _globalBuffer;
    private final CfgAbsRootAnchor _cfgAbsRootAnchor;
    private final TransManager _tm;

    @Inject
    HdMightCreateNotification(MightCreate mc,
            ScanSessionQueue ssq,
            IDeletionBuffer globalBuffer,
            CfgAbsRootAnchor cfgAbsRootAnchor,
            TransManager tm)
    {
        _mc = mc;
        _ssq = ssq;
        _globalBuffer = globalBuffer;
        _cfgAbsRootAnchor = cfgAbsRootAnchor;
        _tm = tm;
    }

    @Override
    public void handle_(EIMightCreateNotification ev, Prio prio)
    {
        try {
            MightCreate.Result res;
            Trans t = _tm.begin_();
            try {
                 res = _mc.mightCreate_(new PathCombo(_cfgAbsRootAnchor, ev._absPath),
                         _globalBuffer, t);
                 t.commit_();
            } finally {
                t.end_();
            }
            if (res == Result.NEW_OR_REPLACED_FOLDER) {
                // We need to scan subdirectories of new folders because they could have data
                // placed in them faster than we can register a watch on Linux.
                _ssq.scanImmediately_(Collections.singleton(ev._absPath), true);
            }
        } catch (Exception e) {
            // On any exception, perform a full scan.
            l.warn("full scan triggered by " + Util.e(e));
            _ssq.scanAfterDelay_(Collections.singleton(_cfgAbsRootAnchor.get()), true);
        }
    }
}
