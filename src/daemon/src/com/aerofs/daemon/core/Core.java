package com.aerofs.daemon.core;

import com.aerofs.daemon.core.db.CoreDBSetup;
import com.aerofs.daemon.core.first.FirstLaunch;
import com.aerofs.daemon.core.phy.ScanCompletionCallback;
import com.aerofs.daemon.core.syncstatus.SyncStatusNotificationSubscriber;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.verkehr.VerkehrNotificationSubscriber;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.Cfg;
import com.google.inject.Inject;

import com.aerofs.daemon.IModule;
import com.aerofs.daemon.core.acl.ACLNotificationSubscriber;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.core.notification.RitualNotificationServer;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.update.DaemonPostUpdateTasks;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.db.CoreDBCW;

import java.io.IOException;

public class Core implements IModule
{
    private final FirstLaunch _fl;
    private final CoreDBCW _dbcw;
    private final NativeVersionControl _nvc;
    private final ImmigrantVersionControl _ivc;
    private final IPhysicalStorage _ps;
    private final IStores _ss;
    private final LinkStateService _lss;
    private final Transports _tps;
    private final TC _tc;
    private final VerkehrNotificationSubscriber _vksub;
    private final ACLNotificationSubscriber _aclsub;
    private final SyncStatusNotificationSubscriber _sssub;
    private final UnicastInputOutputStack _stack;
    private final ILinker _linker;
    private final RitualNotificationServer _notifier;
    private final DaemonPostUpdateTasks _dput;
    private final CoreDBSetup _dbsetup;
    private final CoreProgressWatcher _cpw;

    @Inject
    public Core(
            FirstLaunch fl,
            TC tc,
            CoreIMCExecutor imce,
            Transports tps,
            LinkStateService lss,
            IPhysicalStorage ps,
            NativeVersionControl nvc,
            ImmigrantVersionControl ivc,
            CoreDBCW dbcw,
            VerkehrNotificationSubscriber vksub,
            ACLNotificationSubscriber aclsub,
            SyncStatusNotificationSubscriber sssub,
            UnicastInputOutputStack stack,
            RitualNotificationServer notifier,
            ILinker linker,
            DaemonPostUpdateTasks dput,
            CoreDBSetup dbsetup,
            IStores ss,
            CoreProgressWatcher cpw)
    {
        _imce2core = imce.imce();
        _fl = fl;
        _tc = tc;
        _ss = ss;
        _lss = lss;
        _tps = tps;
        _ps = ps;
        _nvc = nvc;
        _ivc = ivc;
        _dbcw = dbcw;
        _vksub = vksub;
        _aclsub = aclsub;
        _sssub = sssub;
        _stack = stack;
        _linker = linker;
        _notifier = notifier;
        _dput = dput;
        _dbsetup = dbsetup;
        _cpw = cpw;
    }

    @Override
    public void init_() throws Exception
    {
        _dbcw.get().init_();
        // setup core DB if needed
        if (!_dbsetup.isSetupDone_()) _dbsetup.setup_();
        // must run dput immediately after database initialization and before other components, as
        // required by IDaemonPostUpdateTask.run()
        _dput.run();
        _nvc.init_();
        _ivc.init_();
        _ps.init_();
        _linker.init_();
        _ss.init_();
        _tps.init_();
        _stack.init_();
        _aclsub.init_();
        _sssub.init_();
        _notifier.init_();

// SP Daemon support is temporarily disabled. Search the code base for "SP_DID" and references to
// Cfg.isSP() when restoring the function.
//
//        _sched.schedule(new AbstractEBSelfHandling()
//        {
//            @Override
//            public void handle_()
//            {
//                try {
//                    initCore_();
//                } catch (Exception e) {
//                    SystemUtil.fatal(e);
//                }
//            }
//        }, 0);
    }

    // It's a hack. FIXME using injection
    private static IIMCExecutor _imce2core;

    public static IIMCExecutor imce()
    {
        return _imce2core;
    }

    @Override
    public void start_()
    {
        // core health

        _cpw.start_();

        // start Ritual notifications as we use them to report progress
        _notifier.start_();

        if (_fl.onFirstLaunch_(new CoreScanCompletionCallback())) {
            _tc.start_();
        } else {
            startAll_();

            // because events handling is kicked off once tc starts, and replying
            // to heart beats requires that everything is ready, tc should start last.
            _tc.start_();
        }
    }

    private class CoreScanCompletionCallback extends ScanCompletionCallback {
        /**
         * The start up sequence is sort of messy and the addition of an uninterruptible scan on
         * first launch brings many issues to the surface:
         *   * the first scan uses ScanSessionQueue and needs an operational TC
         *   * the regular startup sequence needs to be done from a non-core thread
         *   * events enqueued by the regular startup sequence should be delayed until the end of
         *   said startup sequence
         *   * the end-of-scan callback is run in a core thread
         *
         *   => we need some absurdly contorted gymnastic to release the core lock around a
         *   temporary thread that executes the regular startup sequence
         *   => we need to suspend event processing in TC during that time
         */
        @Override
        public void done_()
        {
            _tc.suspend_();

            Token tk = _tc.acquire_(Cat.UNLIMITED, "first-launch");
            try {
                TCB tcb = null;
                try {
                    tcb = tk.pseudoPause_("first-launch");

                    Thread t = new Thread() {
                        @Override
                        public void run()
                        {
                            startAll_();
                        }
                    };

                    t.start();
                    t.join();
                } finally {
                    if (tcb != null) tcb.pseudoResumed_();
                }
            } catch (Exception e) {
                SystemUtil.fatal(e);
            } finally {
                tk.reclaim_();
            }

            _tc.resume_();
        }
    }

    private void startAll_()
    {
        // transports

        _tps.start_();
        _lss.start_();

        // rest of the system

        _linker.start_();
        _vksub.start_();
    }

// SP Daemon support is temporarily disabled. Search the code base for "SP_DID" and references to
// Cfg.isSP() when restoring the function.
//
//    // this method is called in a core thread
//    private void initCore_()
//            throws ExNoResource, IOException, ExFormatError
//    {
//        if (Cfg.isSP()) {
//            String tcpEndpoint = Cfg.db().getNullable(Key.TCP_ENDPOINT);
//            if (tcpEndpoint == null) {
//                throw new ExFormatError("must set " + Key.TCP_ENDPOINT + " for sp");
//            }
//
//            if (!tcpEndpoint.equals(L.spEndpoint())) {
//                throw new ExFormatError("sp tcp endpoint values doesn't match");
//            }
//
//        } else {
//            // configure SP's transport. TODO move it to TCP?
//            for (ITransport tp : _tps.getAll_()) {
//                tp.q().enqueueThrows(
//                        new EOTransportReconfigRemoteDevice(L.spEndpoint(), SP_DID),
//                        _tc.prio());
//            }
//        }
//    }
}
