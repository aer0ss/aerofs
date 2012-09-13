package com.aerofs;

import java.io.File;
import java.io.IOException;

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
import com.aerofs.lib.spsv.SVClient;

public class Main {
    final static Logger l = Util.l(Main.class);

    private static final Object CONTROLLER_NAME = "controller";
    private static final Object DAEMON_NAME = "daemon";
    private static final Object FSCK_NAME = "fsck";

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

        String app = null;
        String invite = null;
        if (cmdarg == null) {
            app = C.GUI_NAME;
        } else if (cmdarg.equals(C.GUI_NAME)) {
            app = C.GUI_NAME;
        } else {
            app = "gui";
            invite = cmdarg;
        }

        if (invite == null) mainImpl(rtRoot, app);
        else mainImpl(rtRoot, app, invite);

        return 0;
    }

    // arguments: <rtroot> <app> [appargs ...]
    public static void main(String[] args)
    {
        final int MAIN_ARGS = 2;
        if (args.length < MAIN_ARGS) {
            System.err.println("insufficient arguments");
            System.exit(C.EXIT_CODE_BAD_ARGS);
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
            // I don't know how to output to system.log on mac/linux. so use
            // the command line as a quick/dirty approach
            try {
                Util.execForeground("logger" , "error init log4j: " + e);
            } catch (Exception e2) {
                System.err.println("can't output to system logger. ignored");
            }
            System.err.println("error init log4j: " + e);
            System.exit(C.EXIT_CODE_CANNOT_INIT_LOG4J);
        }

        Util.setDefaultUncaughtExceptionHandler();

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
                SVClient.logSendDefectSyncIgnoreError(true, "failed in main()", e);
            } else {
                SVClient.logSendDefectSyncNoCfgIgnoreError(true, "failed in main()", e,
                        "unknown", rtRoot);
            }
            System.exit(C.EXIT_CODE_EXCEPTION_IN_MAIN);
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
        else cls = null;

        // fail over to UI programs
        if (cls == null) cls = Class.forName("com.aerofs.Program");

        // load config
        if (!Cfg.inited()) {
            try {
                Cfg.init_(rtRoot, ui || controller || prog.equals(DAEMON_NAME));
                l.info("id " + Cfg.did().toStringFormal() + (Cfg.isSP() ? " SP mode" : ""));
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
