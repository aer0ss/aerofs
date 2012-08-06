package com.aerofs.cli;

import com.aerofs.controller.ControllerBadCredentialListener;
import com.aerofs.controller.ControllerService;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Util;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.ui.UI;

public class CLIProgram implements IProgram {

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        // "cc" stands for CLI native library in C
        Util.initDriver("cc");
        ControllerService.init(rtRoot, UI.notifier());
        SPBlockingClient.setListener(new ControllerBadCredentialListener());
        CLI cli = new CLI(rtRoot);
        UI.set(cli);
        cli.enterMainLoop_();
    }
}
