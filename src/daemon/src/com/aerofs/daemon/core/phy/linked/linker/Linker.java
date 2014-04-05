package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.phy.linked.linker.notifier.INotifier;
import com.aerofs.daemon.core.phy.ScanCompletionCallback;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.Prio;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class Linker implements ILinker, LinkerRootMap.IListener
{
    private static final Logger l = Loggers.getLogger(Linker.class);

    // The priority used across the linker
    public static final Prio PRIO = Prio.LO;

    private final CoreScheduler _sched;
    private final INotifier _notifier;
    private final LinkerRootMap _lrm;

    /**
     * @return whether the name indicate an internal file/folder and should therefore be ignored
     *
     * This is used to filter out tag file (.aerofs), aux root (.aerofs.aux) and any other internal
     * file/folder that might be used in the future (.aerofs.seed comes to mind)
     */
    public static boolean isInternalFile(String name)
    {
        return name.startsWith(".aerofs");
    }

    public static boolean isInternalPath(String path)
    {
        return path.contains(File.separator + ".aerofs");
    }

    @Inject
    public Linker(CoreScheduler sched, INotifier.Factory factNotifier, LinkerRoot.Factory factLR,
            LinkerRootMap lrm)
    {
        _sched  = sched;
        _notifier = factNotifier.create();
        _lrm = lrm;
        _lrm.setFactory(factLR);
        _lrm.addListener_(this);
    }

    @Override
    public void init_()
    {
        // add roots from config db, adding watches and starting scan as needed
        // NB: any root that cannot be added is simply ignored
        _lrm.init_();
    }

    @Override
    public void restoreRoot_(SID sid, String absPath, Trans t)
            throws SQLException, IOException
    {
        _lrm.link_(sid, absPath, t);
    }

    @Override
    public void start_()
    {
        try {
            // start the notifier before scanning to avoid losing notifications.
            l.info("start notifier");
            _notifier.start_();
        } catch (IOException e) {
            // TODO: special exit code for Linker start error?
            SystemUtil.fatal("failed to start linker " + Util.e(e));
        }
    }

    @Override
    public void addingRoot_(final LinkerRoot root) throws IOException
    {
        l.info("add root watch {}", root);
        root._watchId = _notifier.addRootWatch_(root);

        // avoid duplicate scans on first launch
        if (Cfg.db().getBoolean(Key.FIRST_START)) return;

        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                l.info("begin scan {}", root.sid());
                root.recursiveScanImmediately_(new ScanCompletionCallback() {
                    @Override
                    public void done_()
                    {
                        // cleanup seed file at the end of the first scan
                        root.OIDGenerator().onScanCompletion_();
                        l.info("end scan {}", root.sid());
                    }
                });
            }
        }, 0);
    }

    @Override
    public void removingRoot_(LinkerRoot root) throws IOException
    {
        _notifier.removeRootWatch_(root);
    }

    void fullScan()
    {
        scan(new ScanCompletionCallback());
    }

    @Override
    public void scan(final ScanCompletionCallback callback)
    {
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                scan_(Lists.newArrayList(_lrm.getAllRoots_()), callback);
            }
        }, 0);
    }

    private void scan_(final List<LinkerRoot> roots, final ScanCompletionCallback callback)
    {
        if (roots.isEmpty()) {
            l.info("scan finished");
            callback.done_();
        } else {
            roots.get(0).recursiveScanImmediately_(new ScanCompletionCallback() {
                @Override
                public void done_()
                {
                    scan_(roots.subList(1, roots.size()), callback);
                }
            });
        }
    }
}
