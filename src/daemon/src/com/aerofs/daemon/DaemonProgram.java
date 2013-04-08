package com.aerofs.daemon;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.daemon.core.CoreModule;
import com.aerofs.daemon.core.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.ex.ExNotShared;
import com.aerofs.daemon.core.ex.ExOutOfSpace;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.multiplicity.multiuser.MultiuserModule;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserModule;
import com.aerofs.daemon.core.phy.block.BlockStorageModules;
import com.aerofs.daemon.core.phy.block.cache.CacheBackendModule;
import com.aerofs.daemon.core.phy.block.gzip.GZipBackendModule;
import com.aerofs.daemon.core.phy.block.local.LocalBackendModule;
import com.aerofs.daemon.core.phy.block.s3.S3BackendModule;
import com.aerofs.daemon.core.phy.linked.LinkedStorageModule;
import com.aerofs.daemon.lib.metrics.RockLogReporter;
import com.aerofs.daemon.ritual.RitualServer;
import com.aerofs.labeling.L;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgModule;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.aerofs.proto.Common.PBException.Type;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import org.slf4j.Logger;

import static java.util.concurrent.TimeUnit.MINUTES;

public class DaemonProgram implements IProgram
{
    private static final Logger l = Loggers.getLogger(DaemonProgram.class);

    private final RitualServer _ritual = new RitualServer();

    public DaemonProgram()
    {
        // Register exception types from the daemon
        Exceptions.registerExceptionTypes(
                new ImmutableMap.Builder<Type, Class<? extends AbstractExWirable>>()
                        .put(Type.ABORTED,                ExAborted.class)
                        .put(Type.EXPELLED,               ExExpelled.class)
                        .put(Type.NO_AVAIL_DEVICE,        ExNoAvailDevice.class)
                        .put(Type.NOT_SHARED,             ExNotShared.class)
                        .put(Type.NO_COMPONENT_WITH_SPECIFIED_VERSION, ExNoComponentWithSpecifiedVersion.class)
                        .put(Type.OUT_OF_SPACE,           ExOutOfSpace.class)
                        .put(Type.UPDATE_IN_PROGRESS,     ExUpdateInProgress.class)
                        .build());

        Util.suppressStackTraces(ExAborted.class, ExNoAvailDevice.class);

        RockLogReporter.enable(10, MINUTES);
    }

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        Util.initDriver("dc"); // "dc" stands for daemon native library in C

        Daemon daemon = inject_();

        daemon.init_();

        daemon.start_();

        _ritual.start_();

        l.error("daemon main thread halted");

        // I don't understand why mac needs this
        if (OSUtil.get().getOSFamily() == OSFamily.OSX) SystemUtil.halt();
    }

    private Daemon inject_()
    {
        ///GuiceUtil.enableLogging();

        Module multiplicityModule  = L.isMultiuser()
                ? new MultiuserModule()
                : new SingleuserModule();

        Stage stage = Stage.PRODUCTION;

        Injector injCore = Guice.createInjector(stage, new CfgModule(), multiplicityModule,
                new CoreModule(), storageModule());

        Injector injDaemon = Guice.createInjector(stage, new DaemonModule(injCore));

        return injDaemon.getInstance(Daemon.class);
    }

    private Module storageModule()
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
        default:
            throw new AssertionError("unsupport storage backend " + storageType);
        }
    }
}
