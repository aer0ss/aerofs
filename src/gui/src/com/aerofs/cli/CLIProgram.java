package com.aerofs.cli;

import com.aerofs.LaunchArgs;
import com.aerofs.controller.SPBadCredentialListener;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.google.common.collect.Lists;

public class CLIProgram implements IProgram
{
    @Override
    public void launch_(final String rtRoot, String prog, String[] args) throws Exception
    {
        Util.initDriver("cc"); // "cc" is the log file that aerofsd will write to

        // process application arguments
        for (String arg : args) processArgument(arg);

        UIGlobals.initialize_(false);
        SPBlockingClient.setBadCredentialListener(new SPBadCredentialListener());

        CLI cli = new CLI();
        UI.set(cli);
        cli.scheduleLaunch(rtRoot, new LaunchArgs(Lists.newArrayList(args)));
        cli.enterMainLoop_();
    }

    /**
     * Processing a single application argument. Supported arguments are:
     *   -E[message] show error message and then immediately exit
     */
    private void processArgument(String arg)
    {
        if (arg.startsWith("-E")) {
            System.err.println(arg.substring("-E".length()));
            ExitCode.CONFIGURATION_INIT.exit();
        }
    }
}
