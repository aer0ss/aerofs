package com.aerofs.gui;

import com.aerofs.LaunchArgs;
import com.aerofs.base.Loggers;
import com.aerofs.defects.Defects;
import com.aerofs.labeling.L;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.InitErrors;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;

public class GUIProgram implements IProgram
{
    private static final Logger l = Loggers.getLogger(GUIProgram.class);

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
                    showInitErrors("", WINDOWS_UNSATISFIED_LINK_ERROR_MESSAGE);
                }
            }
            throw linkError;
        }

        // These are the launch time optional JVM args that can be passed on to the daemon.
        LaunchArgs launchArgs = new LaunchArgs();

        // process JVM arguments
        for (String arg : args) {
            if (arg.startsWith("-X")) {
               launchArgs.addArg(arg);
            }
        }

        if (InitErrors.hasErrorMessages()) {
            showInitErrors(InitErrors.getTitle(), InitErrors.getDescription());
            ExitCode.CONFIGURATION_INIT.exit();
        }

        // TODO (AT): really need to tidy up our launch sequence
        // Defects system initialization is replicated in GUI, CLI, SH, and Daemon. The only
        // difference is how the exception is handled.
        try {
            Defects.init(prog, rtRoot);
        } catch (Exception e) {
            showInitErrors("Failed to initialize the defects system.", "Cause: " + e.toString());
            ExitCode.FAIL_TO_LAUNCH.exit();
        }

        UIGlobals.initialize_(true, launchArgs);

        GUI gui = new GUI();
        UI.set(gui);
        gui.scheduleLaunch(rtRoot);
        gui.enterMainLoop_();
    }

    // N.B. this method is used to show errors that occur in early stages, so we should not use
    // any configuration properties nor any other AeroFS utilities in this method.
    private void showInitErrors(String title, String description)
    {
        Display display = Display.getDefault();
        Shell shell = new Shell(display);

        CLabel icon = new CLabel(shell, SWT.NONE);
        icon.setImage(display.getSystemImage(SWT.ICON_ERROR));

        Label label = new Label(shell, SWT.WRAP);
        Font font = label.getFont();
        FontData fd = font.getFontData()[0];
        label.setFont(SWTResourceManager.getFont(fd.getName(), fd.getHeight() * 11 / 10, SWT.BOLD));
        label.setText(title);

        Link link = new Link(shell, SWT.NONE);
        link.setFont(SWTResourceManager.getFont(fd.getName(), fd.getHeight() * 11 / 10, SWT.NONE));
        link.setText(description);
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                GUIUtil.launch(e.text);
            }
        });

        Button button = new Button(shell, SWT.NONE);
        button.setText(IDialogConstants.OK_LABEL);
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                shell.close();
            }
        });

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 16;
        layout.marginHeight = 16;
        layout.horizontalSpacing = 16;
        layout.verticalSpacing = 16;

        icon.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, true, 1, 3));

        GridData labelData = new GridData(SWT.LEFT, SWT.TOP, true, false);
        labelData.widthHint = 300;
        label.setLayoutData(labelData);

        GridData linkData = new GridData(SWT.LEFT, SWT.TOP, true, false);
        linkData.widthHint = 300;
        link.setLayoutData(linkData);

        GridData buttonData = new GridData(SWT.RIGHT, SWT.BOTTOM, true, false);
        buttonData.widthHint = 80;
        button.setLayoutData(buttonData);

        shell.setDefaultButton(button);
        shell.setLayout(layout);
        shell.pack();
        shell.setVisible(true);

        GUIUtil.centerShell(shell);

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }
}
