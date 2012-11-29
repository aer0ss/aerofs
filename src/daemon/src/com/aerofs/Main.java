package com.aerofs;

import java.io.File;
import java.io.IOException;

import java.lang.reflect.Field;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.id.UserID;
import org.apache.log4j.Logger;

import com.google.inject.CreationException;
import com.google.inject.spi.Message;

import com.aerofs.lib.C;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Util;
import com.aerofs.lib.IProgram.ExProgramNotFound;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;


public class Main {
    final static Logger l = Util.l(Main.class);

    private static final Object CONTROLLER_NAME = "controller";
    private static final Object DAEMON_NAME = "daemon";
    private static final Object FSCK_NAME = "fsck";
    private static final Object UMDC_NAME = "umdc";

    // for windows. used by eclipse.exe
    // args: <"gui" or invitation file> [-rtroot <rtroot>]
    // must put <"gui" or invitation file> as the first arg.
    public int run(String[] args)
    {
        String rtRoot = null;
        String cmdarg = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-rtroot")) {
                rtRoot = args[i + 1];
            } else if (args[i].equals("-startup")) {
                if (i + 2 < args.length && !args[i + 2].startsWith("-")) {
                    cmdarg = args[i + 2];
                }
            }
        }

        if (rtRoot == null) rtRoot = C.DEFAULT_RTROOT;

        String invite = null;
        if (cmdarg != null && !cmdarg.equals(C.GUI_NAME)) {
            invite = cmdarg;
        }

        if (invite == null) mainImpl(rtRoot, C.GUI_NAME);
        else mainImpl(rtRoot, C.GUI_NAME, invite);

        return 0;
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
        String app = args[1];

        String[] appArgs = new String[args.length - MAIN_ARGS];
        for (int i = 0; i < appArgs.length; i++) {
            appArgs[i] = args[i + MAIN_ARGS];
        }

        mainImpl(rtRoot, app, appArgs);
    }

    private static void mainImpl(String rtRoot, String prog, String ... appArgs)
    {
        if (rtRoot.equals(C.DEFAULT_RTROOT)) {
            rtRoot = OSUtil.get().getDefaultRTRoot();
        }

        // Create rtroot folder with the right permissions
        createRtRootIfNotExists(rtRoot);

        // init log4j
        try {
            Util.initLog4J(rtRoot, prog);
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

        SystemUtil.setDefaultUncaughtExceptionHandler();

        try {
            launchProgram(rtRoot, prog, appArgs);
        } catch (Throwable e) {
            if (Cfg.staging()) {
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
            if (Cfg.inited()) {
                SVClient.logSendDefectSyncIgnoreErrors(true, "failed in main()", e);
            } else {
                SVClient.logSendDefectSyncNoCfgIgnoreErrors(true, "failed in main()", e,
                        UserID.UNKNOWN, rtRoot);
            }

            ExitCode.FAIL_TO_LAUNCH.exit();
        }
    }

    private static void launchProgram(String rtRoot, String prog, String ... progArgs)
            throws Exception
    {
        boolean ui = prog.equals(C.GUI_NAME) || prog.equals(C.CLI_NAME);
        boolean controller = prog.equals(CONTROLLER_NAME);

        Class<?> cls;
        // a fast path to UI
        if (ui) cls = Class.forName("com.aerofs.Program");
        else if (controller) cls = Class.forName("com.aerofs.controller.ControllerProgram");
        else if (prog.equals(DAEMON_NAME)) cls = com.aerofs.daemon.DaemonProgram.class;
        else if (prog.equals(FSCK_NAME)) cls = com.aerofs.fsck.FSCKProgram.class;
        else if (prog.equals(UMDC_NAME)) cls = com.aerofs.umdc.UMDCProgram.class;
        else cls = null;

        // fail over to UI programs
        if (cls == null) cls = Class.forName("com.aerofs.Program");

        // load config
        if (!Cfg.inited()) {
            try {
                Cfg.init_(rtRoot, ui || controller || prog.equals(DAEMON_NAME));
                l.debug("id " + Cfg.did().toStringFormal() + (Cfg.isSP() ? " SP mode" : ""));
            } catch (ExNotSetup e) {
                // gui and cli will run setup itself
                if (!ui && !controller) throw e;
            }
        }

        if (Cfg.inited()) l.warn(Cfg.user() + " " + Cfg.did().toStringFormal());

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
