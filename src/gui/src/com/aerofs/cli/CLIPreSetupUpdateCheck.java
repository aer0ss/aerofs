package com.aerofs.cli;

import java.io.IOException;

import com.aerofs.lib.AppRoot;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.Versions;
import com.aerofs.lib.Versions.CompareResult;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.UIGlobals;

class CLIPreSetupUpdateCheck
{
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
        if (Versions.compare(Cfg.ver(), UIGlobals.updater().getServerVersion())
                != CompareResult.NO_CHANGE) {
            System.out.println("An " + L.product() + " update has been found." +
                    " Please " + (OSUtil.isLinux() ?
                        Util.quote("rm -rf " + AppRoot.abs()) :
                        "reinstall")
                    + " and run " + L.product() + " again.");
            System.exit(0);
        }
    }
}
