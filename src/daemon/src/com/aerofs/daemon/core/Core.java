package com.aerofs.daemon.core;

import java.io.IOException;

import com.aerofs.daemon.core.db.CoreDBSetup;
import com.aerofs.daemon.core.syncstatus.SyncStatusNotificationSubscriber;
import com.aerofs.daemon.core.verkehr.VerkehrNotificationSubscriber;
import com.aerofs.lib.SystemUtil;
import com.google.inject.Inject;

import com.aerofs.daemon.IModule;
import com.aerofs.daemon.core.acl.ACLNotificationSubscriber;
import com.aerofs.daemon.core.linker.ILinker;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.core.notification.RitualNotificationServer;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.update.DaemonPostUpdateTasks;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.l.L;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.ex.ExNoResource;

public class Core implements IModule
{
    private final CoreDBCW _dbcw;
    private final NativeVersionControl _nvc;
    private final ImmigrantVersionControl _ivc;
    private final IPhysicalStorage _ps;
    private final IStores _ss;
    private final LinkStateService _lss;
    private final Transports _tps;
    private final CoreScheduler _sched;
    private final TC _tc;
    private final VerkehrNotificationSubscriber _vksub;
    private final ACLNotificationSubscriber _aclsub;
    private final SyncStatusNotificationSubscriber _sssub;
    private final UnicastInputOutputStack _stack;
    private final ILinker _linker;
    private final RitualNotificationServer _notifier;
    private final DaemonPostUpdateTasks _dput;
    private final CoreDBSetup _dbsetup;

    @Inject
    public Core(
            TC tc,
            CoreIMCExecutor imce,
            Transports tps,
            LinkStateService lss,
            CoreScheduler sched,
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
            IStores ss)
    {
        _imce2core = imce.imce();
        _tc = tc;
        _ss = ss;
        _lss = lss;
        _tps = tps;
        _sched = sched;
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
        _ss.init_();
        _tps.init_();
        _stack.init_();
        _linker.init_();
        _aclsub.init_();
        _sssub.init_();
        _notifier.init_();

        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    initCore_();
                } catch (Exception e) {
                    SystemUtil.fatal(e);
                }
            }
        }, 0);
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
        _tps.start_();
        _lss.start_();

        // because events handling is kicked off once tc starts, and replying
        // to heart beats requires that everything is ready, tc should start last.
        _tc.start_();
        _linker.start_();
        _vksub.start_();
        _notifier.start_();
    }

    // this method is called in a core thread
    private void initCore_()
            throws ExNoResource, IOException, ExFormatError
    {
        if (Cfg.isSP()) {
            // verify settings are all right
            if (!Cfg.user().equals(L.get().spUser())) {
                throw new ExFormatError("sp user doesn't match Param.SP_USER");
            }

            String tcpEndpoint = Cfg.db().getNullable(Key.TCP_ENDPOINT);
            if (tcpEndpoint == null) {
                throw new ExFormatError("must set " + Key.TCP_ENDPOINT + " for sp");
            }

            if (!tcpEndpoint.equals(L.get().spEndpoint())) {
                throw new ExFormatError("sp tcp endpoint values doesn't match");
            }

        } else {
            // configure SP's transport. TODO move it to TCP?
// Reconfigure SP endpoint disabled until cloud sync is implemented
//            for (ITransport tp : _tps.getAll_()) {
//                tp.q().enqueueThrows(
//                        new EOTransportReconfigRemoteDevice(L.get().spEndpoint(), L.get().spDID()),
//                        _tc.prio());
//            }
        }
    }
}
