package com.aerofs;

import com.aerofs.base.Loggers;
import com.aerofs.base.properties.Configuration;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.IProgram.ExProgramNotFound;
import com.aerofs.lib.LogUtil;
import com.aerofs.lib.Param;
import com.aerofs.lib.ProgramInformation;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.sv.client.SVClient;
import com.google.inject.CreationException;
import com.google.inject.spi.Message;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

import static com.aerofs.lib.rocklog.RockLog.BaseComponent.CLIENT;

public class Main
{
    final static Logger l = Loggers.getLogger(Main.class);

    private static final Object DAEMON_NAME = "daemon";
    private static final Object FSCK_NAME = "fsck";
    private static final Object UMDC_NAME = "umdc";

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
        String app = args[1];

        String[] appArgs = new String[args.length - MAIN_ARGS];
        for (int i = 0; i < appArgs.length; i++) {
            appArgs[i] = args[i + MAIN_ARGS];
        }

        mainImpl(rtRoot, app, appArgs);
    }

    private static void mainImpl(String rtRoot, String prog, String ... appArgs)
    {
        if (rtRoot.equals(Param.DEFAULT_RTROOT)) {
            rtRoot = OSUtil.get().getDefaultRTRoot();
        }

        // Create rtroot folder with the right permissions
        createRtRootIfNotExists(rtRoot);

        // init log4j
        try {
            LogUtil.initLog4J(rtRoot, prog);
        } catch (IOException e) {
            String msg = "error init log4j: " + Util.e(e);
            // I don't know how to output to system.log on mac/linux. so use
            // the command line as a quick/dirty approach
            try {
                SystemUtil.execForeground("logger", msg);
            } catch (Exception e2) {
                // ignored
            }
            System.err.println(msg);
            ExitCode.FAIL_TO_INITIALIZE_LOGGING.exit();
        }

        // First things first, initialize the configuration subsystem
        final String absoluteRuntimeRoot = new File(rtRoot).getAbsolutePath();
        Configuration.Client.initialize( absoluteRuntimeRoot );

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
        RockLog.init_(CLIENT);
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
        boolean ui = prog.equals(Param.GUI_NAME) || prog.equals(Param.CLI_NAME);

        Class<?> cls;
        // a fast path to UI
        if (ui) cls = Class.forName("com.aerofs.Program");
        else if (prog.equals(DAEMON_NAME)) cls = com.aerofs.daemon.DaemonProgram.class;
        else if (prog.equals(FSCK_NAME)) cls = com.aerofs.fsck.FSCKProgram.class;
        else if (prog.equals(UMDC_NAME)) cls = com.aerofs.umdc.UMDCProgram.class;
        else cls = null;

        // fail over to UI programs
        if (cls == null) cls = Class.forName("com.aerofs.Program");

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
        try {
            ((IProgram) cls.newInstance()).launch_(rtRoot, prog, progArgs);
        } catch (ExProgramNotFound e) {
            String name;
            if (prog.indexOf('.') == -1) {
                name = "com.aerofs.program." + prog + ".Program";
            } else {
                name = prog;
            }
            try {
                cls = Class.forName(name);
            } catch (ClassNotFoundException e2) {
                throw new ExProgramNotFound(prog);
            }
            ((IProgram) cls.newInstance()).launch_(rtRoot, prog, progArgs);
        }
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
