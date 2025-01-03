package com.aerofs.cli;

import com.aerofs.LaunchArgs;
import com.aerofs.MainUtil;
import com.aerofs.base.Loggers;
import com.aerofs.defects.Defects;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgVer;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

public class CLIProgram implements IProgram
{
    private static final Logger l = Loggers.getLogger(CLIProgram.class);

    @Override
    public void launch_(final String rtRoot, String prog, String[] args) throws Exception
    {
        MainUtil.initDriver("cc", rtRoot); // "cc" is the log file that aerofsd will write to

        // TODO (AT): really need to tidy up our launch sequence
        // Defects system initialization is replicated in GUI, CLI, SH, and Daemon. The only
        // difference is how the exception is handled.
        try {
            Defects.init(prog, rtRoot, new CfgLocalUser(), new CfgLocalDID(), new CfgVer());
        } catch (Exception e) {
            System.err.println("Failed to initialize the defects system.\n" +
                    "Cause: " + e.toString());
            l.error("Failed to initialized the defects system.", e);
            ExitCode.FAIL_TO_LAUNCH.exit();
        }

        UIGlobals.initialize_(false, new LaunchArgs(Lists.newArrayList(args)));

        CLI cli = new CLI();
        UI.set(cli);
        cli.scheduleLaunch(rtRoot);
        cli.enterMainLoop_();
    }
}
