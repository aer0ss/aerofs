package com.aerofs;

import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.config.PropertiesRenderer;
import com.aerofs.labeling.L;
import com.aerofs.lib.*;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.configuration.ClientConfigurationLoader;
import com.aerofs.lib.ex.ExDBCorrupted;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.lib.os.OSUtil;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.aerofs.lib.configuration.ClientConfigurationLoader.*;

public class Main
{
    private static final String DMON_PROGRAM_NAME = "daemon";
    private static final String FSCK_PROGRAM_NAME = "fsck";
    private static final String UMDC_PROGRAM_NAME = "umdc";
    private static final String PUMP_PROGRAM_NAME = "pump";

    private final static Logger l = Loggers.getLogger(Main.class);

    private static String getProgramBanner(String rtRoot, String app)
    {
        String strDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return app + " ========================================================\n" + Cfg.ver()
                + " " + strDate + " " + AppRoot.abs() + " " + new File(rtRoot).getAbsolutePath();
    }

    private static void initializeLogging(String rtRoot, String prog)
    {
        try {
            final Level logLevel =
                    Cfg.lotsOfLotsOfLog(rtRoot) ? Level.TRACE
                    : Cfg.lotsOfLog(rtRoot) ? Level.DEBUG
                    : Level.INFO;

            LogUtil.setLevel(logLevel);
            LogUtil.enableFileLogging(rtRoot + "/" + prog + ".log");

            final Date start = new Date();

            // Uncomment this for easier debugging
//            LogUtil.enableConsoleLogging();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                l.debug("TERMINATED [{} / {}]", f.format(start), f.format(new Date()));
            }));

        } catch (Exception e) {
            // FIXME(jP): Can we remove this? Does it ever work?
            String msg = "Error starting log subsystem: " + Util.e(e);
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

        l.info("{}", getProgramBanner(rtRoot, prog));

        if (Cfg.useProfiler()) {
            l.debug("profiler: {}", Cfg.profilerStartingThreshold());
        }
    }

    // arguments: <rtroot> <app> [appargs ...]
    /*
        N.B. the sequence in the main method is fragile, please update the class dependency
        throughout. The following classes are initialized before the configuration system is
        initialized, so they should not depend on the configuration system:
            LoggerFactory, ExitCode, SystemUtil, LibParam, C, LogUtil, Cfg
     */
    public static void main(String[] args)
    {
        final int REQUIRED_ARGS = 2;
        if (args.length < REQUIRED_ARGS) {
            System.err.println("insufficient arguments");
            // accesses LoggerFactory, ExitCode, SystemUtil
            ExitCode.FAIL_TO_LAUNCH.exit();
        }

        // parse arguments
        String rtRoot = args[0];
        String prog = args[1];

        List<String> appArgs = new LinkedList<String>();
        for (int i = REQUIRED_ARGS; i < args.length; i++) {
            appArgs.add(args[i]);
        }

        // access LibParam, C
        if (rtRoot.equals(LibParam.DEFAULT_RTROOT)) {
            rtRoot = OSUtil.get().getDefaultRTRoot();
        }

        // Create rtroot folder with the right permissions
        createRtRootIfNotExists(rtRoot);
        // access LogUtil, Cfg -> reads the flag files
        initializeLogging(rtRoot, prog);

        String appRoot = AppRoot.abs();

        // Set the library path to be APPROOT to avoid library not found exceptions
        // {@see http://blog.cedarsoft.com/2010/11/setting-java-library-path-programmatically/}
        try {
            System.setProperty("java.library.path", appRoot);

            Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
            fieldSysPath.setAccessible(true);
            fieldSysPath.set(null, null); // force sys_paths to re-evaluate java.library.path
        } catch (Exception e) {
            // ignored
            l.warn("The property java.library.path could not be set to {} - {}",appRoot, Util.e(e));
        }

        try {
            ClientConfigurationLoader loader = new ClientConfigurationLoader(appRoot, rtRoot,
                    new PropertiesRenderer());
            Properties properties = loader.loadConfiguration();
            ConfigurationProperties.setProperties(properties);
        } catch (SiteConfigException e) {
            // site config should be properly provisioned by the installation, so any error in
            // loading site config is likely caused by bad installation.
            InitErrors.setErrorMessage(
                    L.product() + " encountered an error while loading the configuration. The " +
                            L.product() + " installation is likely damaged.",
                    "Please download a new installer, reinstall " + L.product() + ", and try " +
                            "again.");
        } catch (HttpConfigException e) {
            // failing to load http config means that we've failed to load from both the remote
            // _and_ local cache.
            InitErrors.setErrorMessage(
                    L.product() + " encountered an error while loading the configuration from " +
                            "the " + L.brand() + " Appliance.",
                    "Please make sure your computer is connected to the network and " +
                            L.product() + " can make network connections to the " +
                            L.brand() + " Appliance on the " +
                            "<a href=\"http://ae.ro/1LDy9vj\">required ports</a>.\n\n" +
                            "Please contact your system administrator if the problem persists.");
        } catch (RenderConfigException e) {
            // failing to render the configuration means that the config server and clients are
            // working with bad values. In other words, the whole system is misconfigured.
            //
            // This situation is dire and the user should just reach out to AeroFS Support.
            InitErrors.setErrorMessage(
                    L.product() + " encountered an error while loading the configuration. The " +
                            L.brand() + " Appliance is likely misconfigured.",
                    "Please contact your system administrator and reach out to " +
                            L.brand() + " Support.");
        }

        if (InitErrors.hasErrorMessages()) {
            // GUIProgram will handle main errors itself
            if (!prog.equals(LibParam.GUI_NAME)) {
                System.err.println(InitErrors.getTitle() + "\n\n" +
                        InitErrors.getDescription());
                ExitCode.CONFIGURATION_INIT.exit();
            }
        }

        //
        // INITIALIZE MAJOR COMPONENTS HERE!!!!!
        //
        ProgramInformation.init_(prog);
        SystemUtil.setDefaultUncaughtExceptionHandler();

        try {
            loadCfg(rtRoot, prog);
            Util.registerLibExceptions();
            launchProgram(rtRoot, prog, appArgs.toArray(new String[0]));
        } catch (ExDBCorrupted e) {
            // this is similar to the message on UiUtil.migrateRtRoot() when Cfg fails to load
            String message = "db corrupted: " + e._integrityCheckResult;
            System.err.println(message);
            l.error(message, e);
            ExitCode.CORRUPTED_DB.exit();
        } catch (Throwable e) {
            String message = "failed in main(): " + Util.e(e);
            System.err.println(message);
            l.error("{}", message, e);
            ExitCode.FAIL_TO_LAUNCH.exit();
        }
    }

    private static boolean isUI(String prog)
    {
        return prog.equals(LibParam.GUI_NAME) || prog.equals(LibParam.CLI_NAME);
    }

    private static void loadCfg(String rtRoot, String prog) throws Exception
    {
        boolean ui = isUI(prog);

        // load config
        if (!Cfg.inited()) {
            try {
                Cfg.init_(rtRoot, ui
                        || prog.equals(LibParam.SH_NAME)
                        || prog.equals(DMON_PROGRAM_NAME)
                        || prog.equals(PUMP_PROGRAM_NAME));
                l.debug("id {}", Cfg.did().toStringFormal());
            } catch (ExNotSetup e) {
                // gui and cli will run setup itself
                if (!ui) throw e;
            }
        }

        if (Cfg.inited()) l.warn("{} {}", Cfg.user(), Cfg.did().toStringFormal());
    }

    private static void launchProgram(String rtRoot, String prog, String ... progArgs)
            throws Exception
    {
        Class<?> cls;
        if (isUI(prog)) cls = Class.forName("com.aerofs.Program"); // fast path to UI
        else if (prog.equals(DMON_PROGRAM_NAME)) cls = com.aerofs.daemon.DaemonProgram.class;
        else if (prog.equals(FSCK_PROGRAM_NAME)) cls = com.aerofs.fsck.FSCKProgram.class;
        else if (prog.equals(UMDC_PROGRAM_NAME)) cls = com.aerofs.umdc.UMDCProgram.class;
        else if (prog.equals(PUMP_PROGRAM_NAME)) cls = com.aerofs.daemon.transport.debug.Pump.class;
        else cls = Class.forName("com.aerofs.Program"); // fail over to UI programs

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
            rtRootFile.setReadable(true, true);     // chmod u+r
            rtRootFile.setWritable(true, true);     // chmod u+w
            rtRootFile.setExecutable(true, true);   // chmod u+x
        }
    }
}
