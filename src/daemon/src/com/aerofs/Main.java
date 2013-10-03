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
import com.aerofs.sv.client.SVClient;
import com.google.common.io.Files;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

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

            // Uncomment this for easier debugging
//            LogUtil.enableConsoleLogging();

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    DateFormat format = new SimpleDateFormat("yyyyMMdd");
                    String strDate = format.format(new Date());
                    l.debug("TERMINATED " + strDate);
                }
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
            l.debug("profiler: " + Cfg.profilerStartingThreshold());
        }
    }

    // arguments: <rtroot> <app> [appargs ...]
    public static void main(String[] args)
    {
        final int REQUIRED_ARGS = 2;
        if (args.length < REQUIRED_ARGS) {
            System.err.println("insufficient arguments");
            ExitCode.FAIL_TO_LAUNCH.exit();
        }

        // parse arguments
        String rtRoot = args[0];
        String prog = args[1];

        String[] appArgs = new String[args.length - REQUIRED_ARGS];
        System.arraycopy(args, REQUIRED_ARGS, appArgs, 0, appArgs.length);

        if (rtRoot.equals(LibParam.DEFAULT_RTROOT)) {
            rtRoot = OSUtil.get().getDefaultRTRoot();
        }

        // Create rtroot folder with the right permissions
        createRtRootIfNotExists(rtRoot);
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
            l.warn("The property java.library.path could not be set to "
                    + appRoot + " - " + Util.e(e));
        }

        // First things first, initialize the configuration subsystem.
        try {
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
                    msg = "Failed to initialize the configuration subsystem. Please verify " +
                            "the configuration service is available.";
                }

                // This is a workaround for the following problem:
                // We have an error message for the user but we are in Main, which lacks the access
                // to resources (SWT) to display the error message. Hence we pass the error
                // message forward as an application argument.
                // FIXME(AT): refactor Main to only start the programs and have individual program
                // run initialization (through inheritance maybe).
                String[] temp = new String[appArgs.length + 1];
                System.arraycopy(appArgs, 0, temp, 0, appArgs.length);
                temp[temp.length - 1] = "-E" + msg;
                appArgs = temp;
            } else {
                System.out.println("failed in main(): " + Util.e(e));
                SVClient.logSendDefectSyncIgnoreErrors(true, "failed in main()", e);
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
            launchProgram(rtRoot, prog, appArgs);
        } catch (ExDBCorrupted e) {
            // this is similar to the message on UiUtil.migrateRtRoot() when Cfg fails to load
            System.out.println("db corrupted: " + e._integrityCheckResult);
            ExitCode.CORRUPTED_DB.exit();
        } catch (Throwable e) {
            System.out.println("failed in main(): " + Util.e(e)); // l.error does not work here.
            SVClient.logSendDefectSyncIgnoreErrors(true, "failed in main()", e);
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
                Cfg.init_(rtRoot, ui || prog.equals(DMON_PROGRAM_NAME) || prog.equals(PUMP_PROGRAM_NAME));
                l.debug("id {}", Cfg.did().toStringFormal());
            } catch (ExNotSetup e) {
                // gui and cli will run setup itself
                if (!ui) throw e;
            }
        }

        if (Cfg.inited()) l.warn(Cfg.user() + " " + Cfg.did().toStringFormal());
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
