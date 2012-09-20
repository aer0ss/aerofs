package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.linker.event.EIMightDeleteNotification;
import com.aerofs.daemon.core.linker.event.EITestPauseOrResumeLinker;
import com.aerofs.daemon.core.linker.notifier.INotifier;
import com.aerofs.daemon.core.linker.scanner.ScanSessionQueue;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Set;

import net.contentobjects.jnotify.JNotifyException;

public class Linker implements ILinker
{
    // The priority used across the linker
    public static final Prio PRIO = Prio.LO;

    private final ScanSessionQueue _ssq;
    private final HdMightCreateNotification _hdMightCreate;
    private final HdMightDeleteNotification _hdMightDelete;
    private final CoreEventDispatcher _disp;
    private final CoreScheduler _sched;
    private final INotifier _notifier;

    private static class NullHandler implements IEventHandler<IEvent>
    {
        @Override
        public void handle_(IEvent ev, Prio prio) {}
    }

    private class HdTestPauseOrResumeLinker extends AbstractHdIMC<EITestPauseOrResumeLinker>
    {
        @Override
        protected void handleThrows_(EITestPauseOrResumeLinker ev, Prio prio) throws Exception
        {
            if (ev._pause) testPause_();
            else testResume_();
        }
    }

    @Inject
    public Linker(CoreEventDispatcher disp, CoreScheduler sched, ScanSessionQueue ssq,
            HdMightDeleteNotification hdMightDelete, HdMightCreateNotification hdMightCreate,
            INotifier.Factory factNotifier)
    {
        _ssq = ssq;
        _hdMightDelete = hdMightDelete;
        _hdMightCreate = hdMightCreate;
        _disp = disp;
        _sched  = sched;
        _notifier = factNotifier.create();
    }

    private void setHandlers_()
    {
        _disp
            .setHandler_(EIMightCreateNotification.class, _hdMightCreate)
            .setHandler_(EIMightDeleteNotification.class, _hdMightDelete);
    }

    private void resetHandlers_()
    {
        NullHandler nh = new NullHandler();
        _disp
            .setHandler_(EIMightCreateNotification.class, nh)
            .setHandler_(EIMightDeleteNotification.class, nh);
    }

    private void fullScan()
    {
        // can't use enqueueBlocking here to avoid deadlocks
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                Set<String> root = Collections.singleton(Cfg.absRootAnchor());
                _ssq.scanImmediately_(root, true);
            }
        }, 0);
    }

    @Override
    public void init_()
    {
        setHandlers_();

        _disp.setHandler_(EITestPauseOrResumeLinker.class, new HdTestPauseOrResumeLinker());
    }

    @Override
    public void start_()
    {
        try {
            // start the notifier before scanning to avoid losing notifications.
            _notifier.start_();
        } catch (JNotifyException e) {
            // Notifier failed to start, either because the root anchor is missing or AeroFS
            // couldn't access it. In either case, hopefully RootAnchorWatch in UI would notify the
            // user about the issue. We don't want to throw it out because 1) start() methods are
            // not supposed to throw, and 2) we'd like to warn the user and proceed instead of
            // terminating the daemon process.
            Util.l(this).warn("ignored: " + Util.e(e));
        }

        fullScan();
    }

    /**
     * For testing only
     */
    void testResume_()
    {
        Util.l(this).warn("resumed");

        setHandlers_();

        fullScan();
    }

    /**
     * For testing only
     */
    void testPause_()
    {
        resetHandlers_();

        Util.l(this).warn("paused");
    }
}
