package com.aerofs.daemon;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.daemon.core.CoreModule;
import com.aerofs.daemon.core.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.ex.ExOutOfSpace;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.multiplicity.multiuser.MultiuserModule;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserModule;
import com.aerofs.daemon.core.phy.block.BlockStorageModules;
import com.aerofs.daemon.core.phy.block.cache.CacheBackendModule;
import com.aerofs.daemon.core.phy.block.gzip.GZipBackendModule;
import com.aerofs.daemon.core.phy.block.local.LocalBackendModule;
import com.aerofs.daemon.core.phy.block.s3.S3BackendModule;
import com.aerofs.daemon.core.phy.block.swift.SwiftBackendModule;
import com.aerofs.daemon.core.phy.linked.LinkedStorageModule;
import com.aerofs.daemon.core.protocol.ExSenderHasNoPerm;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.daemon.rest.RestModule;
import com.aerofs.daemon.rest.RestService;
import com.aerofs.daemon.rest.RestTunnelClient;
import com.aerofs.daemon.ritual.RitualServer;
import com.aerofs.defects.Defects;
import com.aerofs.labeling.L;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgModule;
import com.aerofs.lib.cfg.CfgRestService;
import com.aerofs.lib.nativesocket.NativeSocketAuthenticatorFactory;
import com.aerofs.lib.nativesocket.NativeSocketModule;
import com.aerofs.lib.nativesocket.RitualSocketFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.aerofs.proto.Common.PBException.Type;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import org.slf4j.Logger;

public class DaemonProgram implements IProgram
{
    private static final Logger l = Loggers.getLogger(DaemonProgram.class);

    private RitualServer _ritual =
            new RitualServer(new RitualSocketFile(), NativeSocketAuthenticatorFactory.create());

    public DaemonProgram()
    {
        registerExceptionTypes();
    }

    static void registerExceptionTypes()
    {
        // Register exception types from the daemon
        Exceptions.registerExceptionTypes(
                new ImmutableMap.Builder<Type, Class<? extends AbstractExWirable>>()
                        .put(Type.ABORTED,                ExAborted.class)
                        .put(Type.EXPELLED,               ExExpelled.class)
                        .put(Type.NO_AVAIL_DEVICE,        ExNoAvailDevice.class)
                        .put(Type.NO_COMPONENT_WITH_SPECIFIED_VERSION, ExNoComponentWithSpecifiedVersion.class)
                        .put(Type.OUT_OF_SPACE,           ExOutOfSpace.class)
                        .put(Type.UPDATE_IN_PROGRESS,     ExUpdateInProgress.class)
                        .put(Type.SENDER_HAS_NO_PERM,     ExSenderHasNoPerm.class)
                        .build());

        Util.suppressStackTraces(ExAborted.class, ExNoAvailDevice.class, ExStreamInvalid.class);
    }

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        Util.initDriver("dc"); // "dc" stands for daemon native library in C

        // TODO (AT): really need to tidy up our launch sequence
        // Defects system initialization is replicated in GUI, CLI, SH, and Daemon. The only
        // difference is how the exception is handled.
        try {
            Defects.init(prog, rtRoot);
        } catch (Exception e) {
            System.err.println("Failed to initialize the defects system.\n" +
                    "Cause: " + e.toString());
            l.error("Failed to initialize the defects system.", e);
            ExitCode.FAIL_TO_LAUNCH.exit();
        }

        Daemon daemon = inject_();

        // need to start Ritual server before Core.init_ to send ExUpdating during DPUT
        boolean ritualEarlyStart = Cfg.hasPendingDPUT();
        if (ritualEarlyStart) {
            _ritual.start_();
        }

        daemon.init_();

        daemon.start_();

        // if no DPUT is to be performed, delay Ritual start
        // this is especially important on the first launch as we don't want to mislead
        // the UI into thinking indexing is in progress before the daemon is properly started
        if (!ritualEarlyStart) {
            _ritual.start_();
        }

        l.error("daemon main thread halted");

        // I don't understand why mac needs this
        if (OSUtil.get().getOSFamily() == OSFamily.OSX) halt();
    }

    private void halt()
    {
        Object obj = new Object();
        synchronized (obj) { ThreadUtil.waitUninterruptable(obj); }
    }

    private Daemon inject_()
    {
        ///GuiceUtil.enableLogging();

        Stage stage = Stage.PRODUCTION;

        Injector injCore = Guice.createInjector(stage, new CfgModule(), getMultiplicityModule(),
                new CoreModule(), getStorageModule(), new RestModule(), new NativeSocketModule());

        Injector injDaemon = Guice.createInjector(stage, new DaemonModule(injCore));

        Daemon d = injDaemon.getInstance(Daemon.class);

        // NB: the RestService MUST be started AFTER creation of the Daemon instance or Guice
        // throws a fit
        if (new CfgRestService().isEnabled()) {
            injCore.getInstance(RestService.class).start();
            RestTunnelClient rc = injCore.getInstance(RestTunnelClient.class);
            rc.start();

            // try to cleanly sever tunnel connection on exit to prevent the gateway
            // from directing requests into a dead connection
            Runtime.getRuntime().addShutdownHook(new Thread(rc::stop));
        }

        return d;
    }

    private Module getMultiplicityModule()
    {
        return L.isMultiuser() ? new MultiuserModule() : new SingleuserModule();
    }

    private Module getStorageModule()
    {
        StorageType storageType = Cfg.storageType();
        switch (storageType) {
        case LINKED:
            return new LinkedStorageModule(L.isMultiuser());
        case LOCAL:
            /**
             * NB: Do not change the proxy chain in a backward incompatible way unless you write
             * a DPUT to convert all user data.
             */
            return BlockStorageModules.proxy(new LocalBackendModule(),
                    new GZipBackendModule());
        case S3:
            /**
             * NB: Do not ever change the proxy chain in a backward incompatible way unless you
             * write a DPUT to convert all (known) user data. Note that you'd have to change the
             * storage schema on the S3 side to avoid conflicts with unknown user data (i.e blocks
             * leftover from previous installs of S3 client on the same bucket).
             */
            return BlockStorageModules.proxy(new S3BackendModule(),
                    new CacheBackendModule(), new GZipBackendModule());
        case SWIFT:
            return BlockStorageModules.proxy(new SwiftBackendModule(),
                    new CacheBackendModule(), new GZipBackendModule());
        default:
            throw new AssertionError("unsupport storage backend " + storageType);
        }
    }
}
