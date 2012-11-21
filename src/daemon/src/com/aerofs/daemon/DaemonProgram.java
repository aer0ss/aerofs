package com.aerofs.daemon;

import com.aerofs.daemon.core.CoreModule;
import com.aerofs.daemon.core.phy.block.local.LocalStorageModule;
import com.aerofs.daemon.core.phy.linked.LinkedStorageModule;
import com.aerofs.daemon.core.multiplicity.multiuser.MultiuserModule;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserModule;
import com.aerofs.daemon.ritual.RitualServer;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgModule;
import com.aerofs.s3.S3Module;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

public class DaemonProgram implements IProgram
{
    private final RitualServer _ritual = new RitualServer();

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        // "dc" stands for daemon native library in C
        Util.initDriver("dc");

        Daemon daemon = inject_();

        daemon.init_();

        daemon.start_();

        _ritual.start_();

        Util.l().error("daemon main thread halted");

        // I don't understand why mac needs this
        if (OSUtil.get().getOSFamily() == OSFamily.OSX) SystemUtil.halt();
    }

    private Daemon inject_()
    {
        ///GuiceUtil.enableLogging();

        Module storageModule;
        Module multiplicityModule;
        if (Cfg.db().getBoolean(Key.MULTIUSER)) {
            multiplicityModule = new MultiuserModule();
            storageModule = new LocalStorageModule();
        } else {
            multiplicityModule = new SingleuserModule();
            storageModule = new LinkedStorageModule();
        }

        if (Cfg.db().getNullable(Key.S3_BUCKET_ID) != null) storageModule = new S3Module();

        Stage stage = Stage.PRODUCTION;

        Injector injCore = Guice.createInjector(stage, new CfgModule(), multiplicityModule,
                new CoreModule(), storageModule);

        Injector injDaemon = Guice.createInjector(stage, new DaemonModule(injCore));

        return injDaemon.getInstance(Daemon.class);
    }
}
