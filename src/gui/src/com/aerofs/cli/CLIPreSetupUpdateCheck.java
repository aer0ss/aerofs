package com.aerofs.cli;

import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.S;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.Versions;
import com.aerofs.lib.Versions.CompareResult;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.UIGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class CLIPreSetupUpdateCheck
{
    private static final Logger l = LoggerFactory.getLogger(CLIPreSetupUpdateCheck.class);

    public void run() throws IOException
    {
        System.out.println(S.CHECKING_FOR_DINOSAURS);

        //TODO (GS): Refactor this so that we call updater().checkForUpdate()
        // At least point 1. below is fixed now that we refactored the Launcher

        // don't use updater().checkForUpdate() because of the intricacy of CLI
        // 1. the updater can't use asyncExec to notify download process unless
        //    CLI.enterMainLoop() is called
        // 2. the updater will restart the process in background, which is bad
        //

        String currentVersion = Cfg.ver();
        String serverVersion = UIGlobals.updater().getServerVersion();

        if (Versions.compare(currentVersion, serverVersion) != CompareResult.NO_CHANGE) {
            String userUpdatePrompt = String.format("An %s update has been found. Please %s and run %s again.",
                    L.product(),
                    (OSUtil.isLinux() ? Util.quote("rm -rf " + AppRoot.abs()) : "reinstall"),
                    L.product());
            System.out.println(userUpdatePrompt);

            ExitCode.NEW_VERSION_AVAILABLE.exit(String.format("current version %s != server version %s", currentVersion, serverVersion));
        }
    }
}
