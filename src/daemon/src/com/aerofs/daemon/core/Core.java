package com.aerofs.daemon.core;

import com.aerofs.base.BaseParam.Audit;
import com.aerofs.daemon.IModule;
import com.aerofs.daemon.core.acl.ACLNotificationSubscriber;
import com.aerofs.daemon.core.activity.ClientAuditEventReporter;
import com.aerofs.daemon.core.charlie.CharlieClient;
import com.aerofs.daemon.core.db.CoreDBSetup;
import com.aerofs.daemon.core.db.TamperingDetection;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.daemon.core.first_launch.FirstLaunch;
import com.aerofs.daemon.core.health_check.HealthCheckService;
import com.aerofs.daemon.core.launch_tasks.DaemonLaunchTasks;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.notification.NotificationService;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.polaris.fetch.ChangeNotificationSubscriber;
import com.aerofs.daemon.core.quota.IQuotaEnforcement;
import com.aerofs.daemon.core.store.Stores;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.core.update.DaemonPostUpdateTasks;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.lib.SystemUtil;
import com.aerofs.metriks.IMetriks;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.verkehr.client.wire.VerkehrPubSubClient;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;


public class Core implements IModule
{
    private final FirstLaunch _fl;
    private final CoreDBCW _dbcw;
    private final NativeVersionControl _nvc;
    private final ImmigrantVersionControl _ivc;
    private final IPhysicalStorage _ps;
    private final Stores _ss;
    private final LinkStateService _lss;
    private final Transports _tps;
    private final TC _tc;
    private final TokenManager _tokenManager;
    private final VerkehrPubSubClient _vk;
    private final ACLNotificationSubscriber _aclsub;
    private final UnicastInputOutputStack _stack;
    private final ILinker _linker;
    private final NotificationService _ns;
    private final RitualNotificationServer _rns;
    private final DaemonPostUpdateTasks _dput;
    private final CoreDBSetup _dbsetup;
    private final TamperingDetection _tamperingDetection;
    private final IMetriks _metriks;
    private final CharlieClient _cc;
    private final HealthCheckService _hcs;
    private final ClientAuditEventReporter _caer;
    private final DaemonLaunchTasks _dlts;
    private final IQuotaEnforcement _quota;
    private final LogicalStagingArea _sa;
    private final ChangeNotificationSubscriber _cnsub;

    @Inject
    public Core(
            FirstLaunch fl,
            TC tc,
            TokenManager tokenManager,
            CoreIMCExecutor imce,
            Transports tps,
            LinkStateService lss,
            IPhysicalStorage ps,
            NativeVersionControl nvc,
            ImmigrantVersionControl ivc,
            CoreDBCW dbcw,
            VerkehrPubSubClient vk,
            ACLNotificationSubscriber aclsub,
            UnicastInputOutputStack stack,
            RitualNotificationServer rns,
            NotificationService ns,
            ILinker linker,
            DaemonPostUpdateTasks dput,
            CoreDBSetup dbsetup,
            TamperingDetection tamperingDetection,
            Stores ss,
            IMetriks metriks,
            CharlieClient charlieClient,
            HealthCheckService hcs,
            ClientAuditEventReporter caer,
            IQuotaEnforcement quota,
            DaemonLaunchTasks dlts,
            LogicalStagingArea sa,
            ChangeNotificationSubscriber cnsub)
    {
        _imce2core = imce.imce();
        _fl = fl;
        _tc = tc;
        _tokenManager = tokenManager;
        _ss = ss;
        _lss = lss;
        _tps = tps;
        _ps = ps;
        _nvc = nvc;
        _ivc = ivc;
        _dbcw = dbcw;
        _vk = vk;
        _aclsub = aclsub;
        _stack = stack;
        _linker = linker;
        _rns = rns;
        _ns = ns;
        _dput = dput;
        _dbsetup = dbsetup;
        _tamperingDetection = tamperingDetection;
        _metriks = metriks;
        _hcs = hcs;
        _cc = charlieClient;
        _caer = caer;
        _dlts = dlts;
        _quota = quota;
        _sa = sa;
        _cnsub = cnsub;
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
        // detect DB tampering before launch tasks as they may interact with the outside world
        // but after DPUTs in case the detection schema changes
        _tamperingDetection.init_();
        _dlts.run();

        // initialize the transport first
        _tps.init_();

        _nvc.init_();
        _ivc.init_();

        // IMPORTANT: Linker need to be initialized before PhysicalStorage as LinkedStorage expects
        // LinkerRootMap to be initialized and that is the responsibility of the Linker
        _linker.init_();
        _ps.init_();

        // this service should not be enabled
        // unless auditing is allowed for this
        // installation
        // TDOO (WW) use DI and polymorphism
        if (Audit.AUDIT_ENABLED) {
            _caer.init_();
        }

        _ss.init_();
        _stack.init_();
        _aclsub.init_();
        _cnsub.init_();
        _ns.init_();
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
        // start the health checks
        _hcs.start_();

        // start Ritual notifications as we use them to report progress
        _rns.start_();

        if (_fl.onFirstLaunch_(this::onFirstLaunchCompleted_)) {
            _tc.start_();
        } else {
            startAll_();

            // because events handling is kicked off once tc starts, and replying
            // to heart beats requires that everything is ready, tc should start last.
            _tc.start_();
        }
    }

    private void onFirstLaunchCompleted_()
    {
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
        _tc.suspend_();

        Token tk = checkNotNull(_tokenManager.acquire_(Cat.UNLIMITED, "first-launch"));
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

    private void startAll_()
    {
        // audit service
        // this service should not be started
        // unless auditing is allowed for this
        // installation
        // TDOO (WW) use DI and polymorphysm
        if (Audit.AUDIT_ENABLED) {
            _caer.start_();
        }

        // metrics
        _metriks.start();

        // transports

        _tps.start_();
        _lss.start();

        // rest of the system

        _sa.start_();
        _ps.start_();
        _linker.start_();
        _vk.start();
        _quota.start_();
        _cc.start();
    }
}
