
// we put it here instead of com.aerofs.gui to facilitate ProGuard so remove
// the entire gui package.
//
package com.aerofs.gui;

import com.aerofs.controller.ControllerBadCredentialListener;
import com.aerofs.controller.ControllerService;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.L;
import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import org.apache.log4j.Logger;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

public class GUIProgram implements IProgram
{
    static final Logger l = Util.l(GUIProgram.class);

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        try {
            // "gc" is the name of the log file for the aerofsd library loaded by the GUI
            Util.initDriver("gc");
        } catch (UnsatisfiedLinkError linkError) {
            // On Windows, a common cause of failure is that the MSVC 2010 redistributables aren't
            // installed. Display a message box to the user so that he can fix the problem
            if (OSUtil.isWindows()) {
                try {
                    System.loadLibrary("msvcp100");
                } catch (UnsatisfiedLinkError e1) {
                    MessageBox msgBox = new MessageBox(new Shell());
                    msgBox.setMessage(L.PRODUCT + " cannot launch because the Microsoft Visual " +
                            "C++ 2010 redistributable package is not installed. Please go to the " +
                            "following URL to download and install it:\n\nhttp://ae.ro/msvc2010");
                    msgBox.open();
                    linkError = new UnsatisfiedLinkError("aerofsd.dll could not load because " +
                            "MSVC 2010 redistributables are missing");
                }
            }
            throw linkError;
        }
        ControllerService.init(rtRoot, UI.notifier());
        SPBlockingClient.setListener(new ControllerBadCredentialListener());
        UI.set(new GUI(rtRoot));
        GUI.get().enterMainLoop_();
    }
}
