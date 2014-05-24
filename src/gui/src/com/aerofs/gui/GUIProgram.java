
// we put it here instead of com.aerofs.gui to facilitate ProGuard so remove
// the entire gui package.
//
package com.aerofs.gui;

import com.aerofs.LaunchArgs;
import com.aerofs.controller.SPBadCredentialListener;
import com.aerofs.labeling.L;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

public class GUIProgram implements IProgram
{
    private static final String WINDOWS_UNSATISFIED_LINK_ERROR_MESSAGE =
            L.product() +
                    " cannot launch because the Microsoft Visual " +
                    "C++ 2010 redistributable package is not installed. Please go to the " +
                    "following URL to download and install it:\n\nhttp://ae.ro/msvcpp2010";

    private static final String WINDOWS_MISSING_MSVC_DLL_EXCEPTION_MESSAGE =
            "aerofsd.dll could not load because MSVC 2010 redistributables are missing";

    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        try {
            Util.initDriver("gc"); // "gc" is the log file that aerofsd will write to
        } catch (UnsatisfiedLinkError linkError) {
            // On Windows, a common cause of failure is that the MSVC 2010 redistributables aren't
            // installed. Display a message box to the user so that he can fix the problem
            if (OSUtil.isWindows()) {
                try {
                    System.loadLibrary("msvcp100");
                } catch (UnsatisfiedLinkError e1) {
                    linkError = new UnsatisfiedLinkError(WINDOWS_MISSING_MSVC_DLL_EXCEPTION_MESSAGE);
                    showError(WINDOWS_UNSATISFIED_LINK_ERROR_MESSAGE);
                }
            }
            throw linkError;
        }
        // These are the launch time optional JVM args that can be passed on to the daemon.
        LaunchArgs launchArgs = new LaunchArgs();
        // process application arguments
        for (String arg : args) {
            processArgument(arg);
            // JVM arguments.
            if (arg.startsWith("-X")) {
               launchArgs.addArg(arg);
            }

        }

        UIGlobals.initialize_(true, launchArgs);
        SPBlockingClient.setBadCredentialListener(new SPBadCredentialListener());

        GUI gui = new GUI();
        UI.set(gui);
        gui.scheduleLaunch(rtRoot);
        gui.enterMainLoop_();
    }

    /**
     * Processing a single application argument. Supported arguments are:
     *   -E[message] show error message and then immediately exit
     */
    private void processArgument(String arg)
    {
        if (arg.startsWith("-E")) {
            showError(arg.substring("-E".length()));
            ExitCode.CONFIGURATION_INIT.exit();
        }
    }

    private void showError(String message)
    {
        MessageBox box = new MessageBox(new Shell(), SWT.OK | SWT.ICON_ERROR);
        box.setMessage(message);
        box.open();
    }
}
