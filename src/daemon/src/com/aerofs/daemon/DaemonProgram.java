package com.aerofs.daemon;

import com.aerofs.daemon.core.CoreModule;
import com.aerofs.daemon.core.phy.linked.LinkedStorageModule;
import com.aerofs.daemon.core.multiplicity.multiuser.MultiuserModule;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserModule;
import com.aerofs.daemon.ritual.RitualServer;
import com.aerofs.lib.C;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgBuildType;
import com.aerofs.lib.cfg.CfgCoreDatabaseParams;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgModule;
import com.aerofs.daemon.core.CoreSchema;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.s3.S3Module;
import com.aerofs.s3.S3Schema;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class DaemonProgram implements IProgram
{
    private final RitualServer _ritual = new RitualServer();

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        // "dc" stands for daemon native library in C
        Util.initDriver("dc");

        Daemon daemon = inject_();

        // if the core DB is missing, set it up
        if (!new File(rtRoot, C.CORE_DATABASE).exists()) setupCoreDB();

        daemon.init_();

        daemon.start_();

        _ritual.start_();

        Util.l().error("daemon main thread halted");

        // I don't understand why mac needs this
        if (OSUtil.get().getOSFamily() == OSFamily.OSX) SystemUtil.halt();
    }

    private Daemon inject_()
    {
        ///GuiceLogging.enable();

        Module storageModule = Cfg.db().getNullable(Key.S3_BUCKET_ID) == null ?
                new LinkedStorageModule() : new S3Module();

        Module sumuModule = Cfg.db().getBoolean(Key.MULTIUSER) ?
                new MultiuserModule() : new SingleuserModule();

        Stage stage = Stage.PRODUCTION;

        Injector injCore = Guice.createInjector(stage, new CfgModule(), sumuModule,
                new CoreModule(), storageModule);

        Injector injDaemon = Guice.createInjector(stage, new DaemonModule(injCore));

        return injDaemon.getInstance(Daemon.class);
    }

    private void setupCoreDB() throws SQLException
    {
        // TODO: refactor this using injection
        CfgBuildType cfgBuildType = new CfgBuildType();
        CfgCoreDatabaseParams cfgCoreDBParams = new CfgCoreDatabaseParams(Cfg.db(), cfgBuildType);
        IDBCW dbcw = DBUtil.newDBCW(cfgCoreDBParams);
        dbcw.init_();
        InjectableDriver dr = new InjectableDriver(new CfgLocalUser());
        try {
            Connection c = dbcw.getConnection();
            new CoreSchema(dbcw, dr).create_();
            c.commit();
            if (Cfg.db().getNullable(Key.S3_BUCKET_ID) != null) {
                new S3Schema(dbcw).create_();
            }
        } finally {
            dbcw.fini_();
        }
    }
}
