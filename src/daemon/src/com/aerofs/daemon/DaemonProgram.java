package com.aerofs.daemon;

import com.aerofs.daemon.core.CoreModule;
import com.aerofs.daemon.core.phy.linked.LinkedStorageModule;
import com.aerofs.daemon.fsi.FSI;
import com.aerofs.daemon.ritual.RitualServer;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgModule;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.aerofs.s3.S3Module;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

import java.util.Collection;
import java.util.Collections;

public class DaemonProgram implements IProgram {

    private final IModule _fsi = new FSI();
    private final RitualServer _ritual = new RitualServer();

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        // "dc" stands for daemon native library in C
        Util.initDriver("dc");

        Daemon daemon = inject_();

        daemon.init_();
        _fsi.init_();

        daemon.start_();
        _fsi.start_();

        _ritual.start_();

        Util.l().error("daemon main thread halted");

        // I don't understand why mac needs this
        if (OSUtil.get().getOSFamily() == OSFamily.OSX) Util.halt();
    }

    private Daemon inject_()
    {
        ///GuiceLogging.enable();

        final Stage stage = Stage.PRODUCTION;
        Module storageModule;
        if (Cfg.db().getNullable(Key.S3_DIR) == null) {
            storageModule = new LinkedStorageModule();
        } else {
            storageModule = new S3Module();
        }
        Injector injCore = Guice.createInjector(stage, new CfgModule(), new CoreModule(), storageModule);
        Injector injDaemon = Guice.createInjector(stage, new DaemonModule(injCore));

        return injDaemon.getInstance(Daemon.class);
    }
}
