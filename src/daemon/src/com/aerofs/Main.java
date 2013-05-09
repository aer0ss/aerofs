package com.aerofs;

import com.aerofs.base.Loggers;
import com.aerofs.base.properties.Configuration;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.ProgramInformation;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;
import com.google.inject.CreationException;
import com.google.inject.spi.Message;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main
{
    private final static Logger l = Loggers.getLogger(Main.class);

    private static final Object DAEMON_NAME = "daemon";
    private static final Object FSCK_NAME = "fsck";
    private static final Object UMDC_NAME = "umdc";
    private static final String STAGING_LOG = "logback-staging.xml";
    private static final String PROD_LOG = "logback-prod.xml";

    private static String getProgramBanner(String rtRoot, String app)
    {
        String strDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return app + " ========================================================\n" +
            Cfg.ver() + (L.isStaging() ? " staging " : " ") +
            strDate + " " + AppRoot.abs() + " " + new File(rtRoot).getAbsolutePath();
    }

    private static void initializeLogging(String rtRoot, String prog)
    {
        boolean enableDebugLogging = Cfg.lotsOfLog(rtRoot);
        boolean enableTraceLogging = Cfg.lotsOfLotsOfLog(rtRoot);

        Level logLevel;
        if (enableTraceLogging) {
            logLevel = Level.TRACE;
        } else if (enableDebugLogging) {
            logLevel = Level.DEBUG;
        } else {
            logLevel = Level.INFO;
        }

        // NB: No logger is set up if this is the shell.
        if (! prog.equals(Param.SH_NAME)) {
            try {
                LogUtil.initializeFromConfigFile(rtRoot, prog, logLevel,
                        L.isStaging() ? STAGING_LOG : PROD_LOG );
            } catch (Exception je) {
                // FIXME(jP): Can we remove this? Does it ever work?
                String msg = "Error starting log subsystem: " + Util.e(je);
                // I don't know how to output to system.logging on mac/linux. so use
                // the command line as a quick/dirty approach
                try {
                    SystemUtil.execForeground("logger", msg);
                } catch (Exception e2) {
                    // ignored
                }

                System.err.println(msg);
                ExitCode.FAIL_TO_INITIALIZE_LOGGING.exit();
            }
        }

        l.info("{}", getProgramBanner(rtRoot, prog));

        if (Cfg.useProfiler()) {
            l.debug("profiler: " + Cfg.profilerStartingThreshold());
        }
    }

    // arguments: <rtroot> <app> [appargs ...]
    public static void main(String[] args)
    {
        final int MAIN_ARGS = 2;
        if (args.length < MAIN_ARGS) {
            System.err.println("insufficient arguments");
            ExitCode.FAIL_TO_LAUNCH.exit();
        }

        // parse arguments
        String rtRoot = args[0];
        String prog = args[1];

        String[] appArgs = new String[args.length - MAIN_ARGS];
        System.arraycopy(args, MAIN_ARGS, appArgs, 0, appArgs.length);

        if (rtRoot.equals(LibParam.DEFAULT_RTROOT)) {
            rtRoot = OSUtil.get().getDefaultRTRoot();
        }

        // Create rtroot folder with the right permissions
        createRtRootIfNotExists(rtRoot);
        initializeLogging(rtRoot, prog);

        // First things first, initialize the configuration subsystem.
        try {
            // TODO (MP) need to pass this the configuration URL (should be stored in the conf db).
            Configuration.Client.initialize(null);
        } catch (Exception e) {
            System.out.println("failed in main(): " + Util.e(e));
            SVClient.logSendDefectSyncIgnoreErrors(true, "failed in main()", e);
            ExitCode.CONFIGURATION_INIT.exit();
        }

        // Set the library path to be APPROOT to avoid library not found exceptions
        // {@see http://blog.cedarsoft.com/2010/11/setting-java-library-path-programmatically/}
        try {
            System.setProperty("java.library.path", AppRoot.abs());

            Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
            fieldSysPath.setAccessible(true);
            fieldSysPath.set(null, null); // force sys_paths to re-evaluate java.library.path
        } catch (Exception e) {
            // ignored
            l.warn("The property java.library.path could not be set to "
                    + AppRoot.abs() + " - " + Util.e(e));
        }

        //
        // INITIALIZE MAJOR COMPONENTS HERE!!!!!
        //

        ProgramInformation.init_(prog);
        ChannelFactories.init_();
        SystemUtil.setDefaultUncaughtExceptionHandler();

        try {
            launchProgram(rtRoot, prog, appArgs);
        } catch (Throwable e) {
            if (L.isStaging()) {
                if (e instanceof CreationException) {
                    CreationException ce = (CreationException)e;
                    for (Message m : ce.getErrorMessages()) {
                        System.out.println(m);
                        System.out.println(m.getSource());
                    }
                }
                System.out.println("failed in main():");
                e.printStackTrace();
            } else {
                System.out.println("failed in main(): " + Util.e(e)); // l.error does not work here.
            }

            SVClient.logSendDefectSyncIgnoreErrors(true, "failed in main()", e);
            ExitCode.FAIL_TO_LAUNCH.exit();
        }
    }

    private static void launchProgram(String rtRoot, String prog, String ... progArgs)
            throws Exception
    {
        boolean ui = prog.equals(LibParam.GUI_NAME) || prog.equals(LibParam.CLI_NAME);

        Class<?> cls;
        // a fast path to UI
        if (ui) cls = Class.forName("com.aerofs.Program");
        else if (prog.equals(DAEMON_NAME)) cls = com.aerofs.daemon.DaemonProgram.class;
        else if (prog.equals(FSCK_NAME)) cls = com.aerofs.fsck.FSCKProgram.class;
        else if (prog.equals(UMDC_NAME)) cls = com.aerofs.umdc.UMDCProgram.class;
        else cls = Class.forName("com.aerofs.Program"); // fail over to UI programs

        // load config
        if (!Cfg.inited()) {
            try {
                Cfg.init_(rtRoot, ui || prog.equals(DAEMON_NAME));
                l.debug("id " + Cfg.did().toStringFormal() + (Cfg.isSP() ? " SP mode" : ""));
            } catch (ExNotSetup e) {
                // gui and cli will run setup itself
                if (!ui) throw e;
            }
        }

        if (Cfg.inited()) l.warn(Cfg.user() + " " + Cfg.did().toStringFormal());

        Util.registerLibExceptions();

        // launch the program
        ((IProgram) cls.newInstance()).launch_(rtRoot, prog, progArgs);
    }

    private static void createRtRootIfNotExists(String rtRoot)
    {
        File rtRootFile = new File(rtRoot);
        if (rtRootFile.mkdirs()) {
            // Set permissions on the rtroot to 700, to prevent other users from trying to read
            // our logs or steal our cert/key.  The java permissions API is terrible.
            // Note: this doesn't actually exclude other users via ACLs on Windows, but it does
            // improve the situation on Unixes.
            rtRootFile.setReadable(false, false);   // chmod a-r
            rtRootFile.setWritable(false, false);   // chmod a-w
            rtRootFile.setExecutable(false, false); // chmod a-x
            rtRootFile.setReadable(true, true);     // chmod o+r
            rtRootFile.setWritable(true, true);     // chmod o+w
            rtRootFile.setExecutable(true, true);   // chmod o+x
        }
    }
}
