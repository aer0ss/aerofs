package com.aerofs.daemon;

import com.aerofs.lib.ExitCode;
import org.apache.log4j.Level;

import com.aerofs.daemon.core.CoreModule;
import com.aerofs.daemon.core.linker.MightCreate;
import com.aerofs.daemon.core.linker.MightDelete;
import com.aerofs.daemon.core.linker.TimeoutDeletionBuffer;
import com.aerofs.daemon.core.net.Download;
import com.aerofs.daemon.core.net.throttling.GlobalLimiter;
import com.aerofs.daemon.core.net.throttling.LimitMonitor;
import com.aerofs.daemon.core.net.throttling.PerDeviceLimiter;
import com.aerofs.daemon.core.phy.linked.LinkedStorageModule;
import com.aerofs.daemon.fsi.FSI;
import com.aerofs.daemon.ritual.RitualServer;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Util;
import com.aerofs.lib.aws.s3.S3CredentialsException;
import com.aerofs.lib.aws.s3.S3PasswordException;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgModule;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.aerofs.s3.S3Module;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

public class DaemonProgram implements IProgram {

    private final IModule _fsi = new FSI();
    private final RitualServer _ritual = new RitualServer();

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        // "dc" stands for daemon native library in C
        Util.initDriver("dc");

        initLogLevels_();

        try {
            Daemon daemon = inject_();

            daemon.init_();
            _fsi.init_();

            daemon.start_();
            _fsi.start_();

            _ritual.start_();

            Util.l().error("daemon main thread halted");

            // I don't understand why mac needs this
            if (OSUtil.get().getOSFamily() == OSFamily.OSX) Util.halt();
        } catch (S3CredentialsException e) {
            ExitCode.BAD_S3_CREDENTIALS.exit();
        } catch (S3PasswordException e) {
            ExitCode.BAD_S3_DATA_ENCRYPTION_PASSWORD.exit();
        }
    }

    // TODO: remove this method
    private void initLogLevels_()
    {
        Util.l(GlobalLimiter.class).setLevel(Level.WARN);
        Util.l(PerDeviceLimiter.class).setLevel(Level.WARN);
        Util.l(LimitMonitor.class).setLevel(Level.WARN);

        Util.l(MightCreate.class).setLevel(Level.INFO);
        Util.l(MightDelete.class).setLevel(Level.INFO);
        Util.l(TimeoutDeletionBuffer.class).setLevel(Level.INFO);
        Util.l(Download.class).setLevel(Level.INFO);

        //
        // set logging overrides on a package basis (works when classes are package local)
        //

        com.aerofs.zephyr.core.LoggingOverride.setLogLevels_();
        com.aerofs.daemon.core.linker.scanner.LoggingOverride.setLogLevels_();
        com.aerofs.daemon.transport.xmpp.jingle.LoggingOverride.setLogLevels_();
        com.aerofs.daemon.transport.xmpp.routing.LoggingOverride.setLogLevels_();
        com.aerofs.daemon.transport.xmpp.zephyr.client.nio.LoggingOverride.setLogLevels_();
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
