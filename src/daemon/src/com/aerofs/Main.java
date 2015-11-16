package com.aerofs;

import com.aerofs.base.Loggers;
import com.aerofs.lib.*;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.ex.ExDBCorrupted;
import com.aerofs.lib.os.OSUtil;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;

public class Main
{
    private static final String DMON_PROGRAM_NAME = "daemon";
    private static final String FSCK_PROGRAM_NAME = "fsck";
    private static final String UMDC_PROGRAM_NAME = "umdc";
    private static final String PUMP_PROGRAM_NAME = "pump";
    private static final String SA_PROGRAM_NAME = "storage_agent";

    private final static Logger l = Loggers.getLogger(Main.class);

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
        MainUtil.createRtRootIfNotExists(rtRoot);

        // access LogUtil, Cfg -> reads the flag files
        MainUtil.initializeLogging(rtRoot, prog);

        String appRoot = AppRoot.abs();
        MainUtil.setLibraryPathToApproot(appRoot);
        MainUtil.initializeConfigurationSystem(appRoot, rtRoot);

        if (InitErrors.hasErrorMessages()) {
            // N.B. this block does not return once entered.
            if (prog.equals(LibParam.GUI_NAME)) {
                try {
                    // N.B. GUIProgram will call exit in this case so this call returns only on
                    // exceptions.
                    launchProgram(rtRoot, prog, appArgs.toArray(new String[0]));
                } catch (Throwable e) {
                    String message = "failed in main(): " + Util.e(e);
                    System.err.println(message);
                    l.error("{}", message, e);
                    ExitCode.FAIL_TO_LAUNCH.exit();
                }
            } else {
                System.err.println(InitErrors.getTitle() + "\n\n" +
                        InitErrors.getDescription());
                ExitCode.CONFIGURATION_INIT.exit();
            }
        }

        //
        // INITIALIZE MAJOR COMPONENTS HERE!!!!!
        //
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

        if (Cfg.inited()) l.info("{} {}", Cfg.user(), Cfg.did().toStringFormal());
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
        else if (prog.equals(SA_PROGRAM_NAME)) throw new IllegalArgumentException("Cannot launch storage agent.");
        else cls = Class.forName("com.aerofs.Program"); // fail over to UI programs

        // launch the program
        ((IProgram) cls.newInstance()).launch_(rtRoot, prog, progArgs);
    }
}
