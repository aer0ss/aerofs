package com.aerofs;

import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.ProgramInformation;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.configuration.ClientConfigurationLoader;
import com.aerofs.lib.configuration.ClientConfigurationLoader.ConfigurationException;
import com.aerofs.lib.configuration.ClientConfigurationLoader.IncompatibleModeException;
import com.aerofs.lib.configuration.HttpsDownloader;
import com.aerofs.lib.ex.ExDBCorrupted;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.lib.os.OSUtil;
import com.google.common.io.Files;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

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

        // First things first, initialize the configuration subsystem.
        try {
            // initializes configuration
            initializeConfigurationSystem(appRoot);
            writeCACertToFile(appRoot);
        } catch (Exception e) {
            // WARNING: the following logic is fragile, the root problem is that
            // initializeConfigurationSystem() needs to be reworked and updates its signature to
            // explicitly throw IncompatibleModeException instead.
            if (prog.equals(LibParam.GUI_NAME) || prog.equals(LibParam.CLI_NAME)) {
                String msg;
                if (e instanceof ConfigurationException
                        && e.getCause() instanceof IncompatibleModeException) {
                    msg = "The application is configured to the wrong mode. Please reinstall " +
                            L.product() + ".";
                } else if (e instanceof IOException) {
                    msg = L.product() + " failed to save the configuration to a file. " +
                            "Please make sure the disk is not full and " + L.product() + " has " +
                            "permission to write to " + appRoot;
                } else {
                    msg = "Unable to launch: configuration error.\n\nPlease verify that your " +
                          L.brand() + " Appliance is reachable on the required ports (see " +
                          "http://ae.ro/1kH9UgV for details).\n\nContact your systems administrator " +
                          "if the problem persists.";
                }

                // This is a workaround for the following problem:
                // We have an error message for the user but we are in Main, which lacks the access
                // to resources (SWT) to display the error message. Hence we pass the error
                // message forward as an application argument.
                // FIXME(AT): refactor Main to only start the programs and have individual program
                // run initialization (through inheritance maybe).
                appArgs.add("-E" + msg);
            } else {
                System.err.println("failed in main(): " + Util.e(e));
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
            l.error("{}" + message, e);
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
            rtRootFile.setReadable(true, true);     // chmod o+r
            rtRootFile.setWritable(true, true);     // chmod o+w
            rtRootFile.setExecutable(true, true);   // chmod o+x
        }
    }

    private static void initializeConfigurationSystem(String appRoot)
            throws ConfigurationException
    {
        ClientConfigurationLoader loader = new ClientConfigurationLoader(new HttpsDownloader());
        ConfigurationProperties.setProperties(loader.loadConfiguration(appRoot));
        l.debug("Client configuration initialized");
    }

    private static void writeCACertToFile(String approot) throws IOException
    {
        // Write the new cacert.pem to the approot for use by other parts of the system.
        // TODO (MP) remove this and have everyone use Cfg.cacert() directly.
        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT)
        {
            Files.write(PrivateDeploymentConfig.BASE_CA_CERTIFICATE.getBytes(),
                    new File(approot, LibParam.CA_CERT));
        }
    }
}
