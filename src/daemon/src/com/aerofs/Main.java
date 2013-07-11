package com.aerofs;

import com.aerofs.base.BaseParam;
import com.aerofs.base.Loggers;
import com.aerofs.config.DynamicConfiguration;
import com.aerofs.lib.LibParam.CA;
import com.aerofs.lib.ex.ExDBCorrupted;
import com.aerofs.lib.S;
import com.aerofs.lib.configuration.ClientConfigurationLoader;
import com.aerofs.lib.configuration.ClientConfigurationLoader.IncompatibleModeException;
import com.aerofs.lib.configuration.HttpsDownloader;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.ChannelFactories;
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
import com.aerofs.lib.properties.DynamicPropertySource;
import com.aerofs.sv.client.SVClient;
import com.google.common.io.Files;
import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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
        boolean enableDebugLogging = Cfg.lotsOfLog(rtRoot);
        boolean enableTraceLogging = Cfg.lotsOfLotsOfLog(rtRoot);
        boolean enableConsoleOutput = false; // TODO: enable simply when in development

        Level logLevel;
        if (enableTraceLogging) {
            logLevel = Level.TRACE;
        } else if (enableDebugLogging) {
            logLevel = Level.DEBUG;
        } else {
            logLevel = Level.INFO;
        }

        try {
            LogUtil.initialize(rtRoot, prog, logLevel, enableConsoleOutput);
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

        // First things first, initialize the configuration subsystem.
        try {
            initializeConfigurationSystem(AppRoot.abs());
            writeCACertToFile(AppRoot.abs());
        } catch (Exception e) {
            if (prog.equals(LibParam.GUI_NAME) || prog.equals(LibParam.CLI_NAME)) {
                String msg = null;

                if (e instanceof ConfigurationException) msg = S.ERR_CONFIG_UNAVAILABLE;
                else if (e instanceof IncompatibleModeException) msg = S.ERR_INCOMPATIBLE_MODE;

                if (msg != null) {
                    String[] temp = new String[appArgs.length + 1];
                    System.arraycopy(appArgs, 0, temp, 0, appArgs.length);
                    temp[temp.length - 1] = "-E" + msg;
                    appArgs = temp;
                }
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
        ChannelFactories.init_();
        SystemUtil.setDefaultUncaughtExceptionHandler();

        try {
            launchProgram(rtRoot, prog, appArgs);
        } catch (ExDBCorrupted e) {
            System.out.println("db corrupted: " + e._integrityCheckResult);
            ExitCode.CORRUPTED_DB.exit();
        } catch (Throwable e) {
            System.out.println("failed in main(): " + Util.e(e)); // l.error does not work here.
            SVClient.logSendDefectSyncIgnoreErrors(true, "failed in main()", e);
            ExitCode.FAIL_TO_LAUNCH.exit();
        }
    }

    private static void launchProgram(String rtRoot, String prog, String ... progArgs)
            throws Exception
    {
        boolean ui = prog.equals(LibParam.GUI_NAME) || prog.equals(LibParam.CLI_NAME);

        Class<?> cls;
        if (ui) cls = Class.forName("com.aerofs.Program"); // fast path to UI
        else if (prog.equals(DMON_PROGRAM_NAME)) cls = com.aerofs.daemon.DaemonProgram.class;
        else if (prog.equals(FSCK_PROGRAM_NAME)) cls = com.aerofs.fsck.FSCKProgram.class;
        else if (prog.equals(UMDC_PROGRAM_NAME)) cls = com.aerofs.umdc.UMDCProgram.class;
        else if (prog.equals(PUMP_PROGRAM_NAME)) cls = com.aerofs.daemon.transport.pump.Pump.class;
        else cls = Class.forName("com.aerofs.Program"); // fail over to UI programs

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

    private static void initializeConfigurationSystem(String appRoot)
            throws ConfigurationException, IncompatibleModeException
    {
        ClientConfigurationLoader loader = new ClientConfigurationLoader(new HttpsDownloader());
        DynamicConfiguration.initialize(loader.loadConfiguration(appRoot));
        BaseParam.setPropertySource(new DynamicPropertySource());
        l.debug("Client configuration initialized");
    }

    private static void writeCACertToFile(String approot)
    {
        // Write the new cacert.pem to the approot for use by other parts of the system.
        // TODO (MP) remove this and have everyone use Cfg.cacert() directly.
        if (CA.CERTIFICATE.get().isPresent())
        {
            try {
                Files.write(CA.CERTIFICATE.get().get().getBytes(),
                        new File(approot, LibParam.CA_CERT));
            } catch (IOException e) {
                l.debug("Failed to write CA cert to disk.", e);
            }
        }
    }
}
