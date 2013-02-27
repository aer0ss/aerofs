package com.aerofs.daemon;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreModule;
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
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgModule;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.aerofs.lib.rocklog.RockLog;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import org.slf4j.Logger;

import static com.aerofs.lib.rocklog.RockLog.BaseComponent.CLIENT;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DaemonProgram implements IProgram
{
    static
    {
        RockLog.init_(CLIENT);
        RockLogReporter.enable(30, SECONDS);
    }

    private static final Logger l = Loggers.getLogger(DaemonProgram.class);

    private final RitualServer _ritual = new RitualServer();

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

        Module storageModule;
        Module multiplicityModule;
        if (L.get().isMultiuser()) {
            multiplicityModule = new MultiuserModule();
            /**
             * NB: Do not change the proxy chain in a backward incompatible way unless you write
             * a DPUT to convert all user data.
             */
            storageModule = BlockStorageModules.proxy(new LocalBackendModule(),
                    new GZipBackendModule());
        } else {
            multiplicityModule = new SingleuserModule();
            storageModule = new LinkedStorageModule();
        }

        if (Cfg.db().getNullable(Key.S3_BUCKET_ID) != null) {
            /**
             * NB: Do not ever change the proxy chain in a backward incompatible way unless you
             * write a DPUT to convert all (known) user data. Note that you'd have to change the
             * storage schema on the S3 side to avoid conflicts with unknown user data (i.e blocks
             * leftover from previous installs of S3 client on the same bucket).
             */
            storageModule = BlockStorageModules.proxy(new S3BackendModule(),
                    new CacheBackendModule(), new GZipBackendModule());
        }

        Stage stage = Stage.PRODUCTION;

        Injector injCore = Guice.createInjector(stage, new CfgModule(), multiplicityModule,
                new CoreModule(), storageModule);

        Injector injDaemon = Guice.createInjector(stage, new DaemonModule(injCore));

        return injDaemon.getInstance(Daemon.class);
    }
}
