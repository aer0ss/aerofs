
// we put it here instead of com.aerofs.gui to facilitate ProGuard so remove
// the entire gui package.
//
package com.aerofs.gui;

import com.aerofs.controller.ControllerBadCredentialListener;
import com.aerofs.controller.ControllerService;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Util;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.ui.UI;
import org.apache.log4j.Logger;

public class GUIProgram implements IProgram
{
    static final Logger l = Util.l(GUIProgram.class);

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        // "dc" stands for GUI native library in C
        Util.initDriver("gc");
        ControllerService.init(rtRoot, UI.notifier());
        SPBlockingClient.setListener(new ControllerBadCredentialListener());
        UI.set(new GUI(rtRoot));
        GUI.get().enterMainLoop_();
    }
}
