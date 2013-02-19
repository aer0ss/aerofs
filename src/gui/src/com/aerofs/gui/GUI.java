package com.aerofs.gui;

import com.aerofs.gui.AeroFSMessageBox.ButtonType;
import com.aerofs.gui.AeroFSMessageBox.IconType;
import com.aerofs.gui.multiuser.MultiuserDlgSetup;
import com.aerofs.gui.multiuser.tray.MultiuserMenuProvider;
import com.aerofs.gui.setup.AbstractDlgSetup;
import com.aerofs.gui.setup.DlgPreSetupUpdateCheck;
import com.aerofs.gui.singleuser.SingleuserDlgSetup;
import com.aerofs.gui.singleuser.tray.SingleuserMenuProvider;
import com.aerofs.gui.tray.SystemTray;
import com.aerofs.lib.InOutArg;
import com.aerofs.labeling.L;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.S;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.IUI;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashSet;

public class GUI implements IUI
{
    private static final Logger l = Util.l(GUI.class);

    private final Display _disp;
    private final Shell _sh;
    private SystemTray _st;
    private final String _rtRoot;

    public SystemTray st() { return _st; }

    public static GUI get()
    {
        return (GUI) UI.get();
    }

    @Override
    public boolean isUIThread()
    {
        return _disp.isDisposed() || _disp.getThread() == Thread.currentThread();
    }

    /**
     * the caller thread will become the UI thread
     */
    GUI(String rtRoot) throws IOException
    {
        _rtRoot = rtRoot;
        try {
            _disp = Display.getDefault();
        } catch (NullPointerException e) {
            /*
             * When no Windowing system is available, a NullPointerException would throw from:
             *         at org.eclipse.swt.graphics.Device.getPrimaryScreen(Unknown Source)
             *         at org.eclipse.swt.graphics.Device.getScreenDPI(Unknown Source)
             *         at org.eclipse.swt.graphics.Device.getDPI(Unknown Source)
             *         at org.eclipse.swt.graphics.Device.init(Unknown Source)
             *         at org.eclipse.swt.widgets.Display.init(Unknown Source)
             *         at org.eclipse.swt.graphics.Device.<init>(Unknown Source)
             *         at org.eclipse.swt.widgets.Display.<init>(Unknown Source)
             *         at org.eclipse.swt.widgets.Display.<init>(Unknown Source)
             *         at org.eclipse.swt.widgets.Display.getDefault(Unknown Source)
             */
            throw new IOException("Graphical interface is not available. Please use the command" +
                    " line interface (" + S.CLI_NAME + ")", e);
        }

        // the grand grand parent shell for all other shells
        _sh = new Shell(_disp);
        // setting the size to 0 would disable the tab key in the setup dialog
        _sh.setSize(1, 1);
        _sh.setText(L.PRODUCT);
        GUIUtil.setShellIcon(_sh);
        GUIUtil.centerShell(_sh);

        // Schedule our launch() method to be called as soon as we enter the main loop
        asyncExec(new Runnable()
        {
            @Override
            public void run() {

                UIUtil.launch(_rtRoot, new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            preLaunch();
                        }
                    }, new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            postLaunch();
                        }
                    });
            }
        });
    }

    private void preLaunch()
    {
        // Create the system tray
        if (L.get().isMultiuser()) {
            _st = new SystemTray(new MultiuserMenuProvider());
        } else {
            _st = new SystemTray(new SingleuserMenuProvider());
        }
    }

    /**
     * this method runs in the ui thread
     */
    private void postLaunch()
    {
        assert UI.get().isUIThread();

        SystemTray st = GUI.get().st();
        if (st != null) st.enableMenu();

        if (OSUtil.isLinux()) UbuntuTraySettings.checkAndUpdateUbuntuTraySettings();

        // Offer to install the shell extension if it's not installed
        if (shellExtensionShouldBeInstalled()) {
            try {
                // Try to install silently
                OSUtil.get().installShellExtension(true);
            } catch (SecurityException e) {
                askUserToInstallShellExt();
            } catch (IOException e) {
                reportShellExtInstallFailed(e);
            }
        }
    }

    private boolean shellExtensionShouldBeInstalled()
    {
        return !L.get().isMultiuser() && OSUtil.get().isShellExtensionAvailable() &&
                !OSUtil.get().isShellExtensionInstalled();
    }

    private void askUserToInstallShellExt()
    {
        UI.get().notify(IUI.MessageType.INFO,
                "Install " + OSUtil.get().getShellExtensionName() + "?",
                "Please click here if you would like to install the " +
                        OSUtil.get().getShellExtensionName() + ".",
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            OSUtil.get().installShellExtension(false);
                        } catch (Exception e) {
                            reportShellExtInstallFailed(e);
                        }
                    }
                });
    }

    private void reportShellExtInstallFailed(Exception e)
    {
        l.warn("Failed to install shell extension: " + Util.e(e));
        UI.get().show(MessageType.WARN, "Failed to install the " +
                OSUtil.get().getShellExtensionName() + ". " + UIUtil.e2msg(e));
    }

    public Display disp()
    {
        return _disp;
    }

    /**
     * @return the great grandpa of all other shells
     */
    public Shell sh()
    {
        return _sh;
    }

    private static IconType mt2it(MessageType mt)
    {
        assert mt != null;

        switch (mt) {
        case WARN: return IconType.WARN;
        case ERROR: return IconType.ERROR;
        case QUESTION: return IconType.QUESTION;
        default: return IconType.INFO;
        }
    }

    /**
     * @param sh non-null to use sheet style
     */
    public void show(@Nullable Shell sh, MessageType mt, String msg)
    {
        boolean sheet;
        if (sh == null) {
            sh = _sh;
            sheet = false;
        } else {
            sheet = true;
        }

        showImpl(sh, sheet, mt, msg);
    }

    public void showImpl(final Shell sh, final boolean sheet,
            final MessageType mt, final String msg)
    {
        exec(new Runnable() {
            @Override
            public void run()
            {
                new AeroFSMessageBox(sh, sheet, msg, mt2it(mt)).open();
            }
        });
    }

    @Override
    public void show(MessageType mt, String msg)
    {
        showImpl(_sh, false, mt, msg);
    }

    @Override
    public void showWithNoShowAgainCheckBox(final MessageType mt,
            final String msg, final OutArg<Boolean> noShow)
    {
        noShow.set(false);

        exec(new Runnable() {
            @Override
            public void run()
            {
                AeroFSMessageBox amb = new AeroFSMessageBox(_sh, false,
                        msg, mt2it(mt), ButtonType.OKAY,
                        S.DONT_SHOW_THIS_MESSAGE_AGAIN);
                amb.open();
                noShow.set(amb.isChecked());
            }
        });
    }

    public void safeAsyncExec(final Widget w, final Runnable run)
    {
        if (!_disp.isDisposed() && !w.isDisposed()) {
            _disp.asyncExec(new Runnable() {
                @Override
                public void run()
                {
                    if (w.isDisposed()) return;
                    assert _disp == w.getDisplay();
                    run.run();
                }
            });
        }
    }

    public void safeTimerExec(final Widget w, long timer, final Runnable run)
    {
        if (!_disp.isDisposed() && !w.isDisposed()) {
            _disp.timerExec((int) timer, new Runnable() {
                @Override
                public void run()
                {
                    if (w.isDisposed()) return;
                    assert _disp == w.getDisplay();
                    run.run();
                }
            });
        }
    }

    public void safeExec(final Widget w, final Runnable run)
    {
        if (!_disp.isDisposed() && !w.isDisposed()) {
            _disp.syncExec(new Runnable() {
                @Override
                public void run()
                {
                    if (w.isDisposed()) return;
                    assert _disp == w.getDisplay();
                    run.run();
                }
            });
        }
    }

    @Override
    public void asyncExec(final Runnable runnable)
    {
        if (!_disp.isDisposed()) _disp.asyncExec(runnable);
    }

    @Override
    public void timerExec(long delay, Runnable runnable)
    {
        assert ((int) delay) == delay;

        if (!_disp.isDisposed()) _disp.timerExec((int) delay, runnable);
    }

    @Override
    public void exec(final Runnable run)
    {
        if (!_disp.isDisposed()) _disp.syncExec(run);
    }

    /**
     * @param sh non-null for sheet-style dialogs
     */
    public boolean ask(Shell sh, MessageType mt, String msg)
    {
        return ask(sh, mt, msg, IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL);
    }

    /**
     * Shows a message box with yes,no buttons for {@code duration} seconds
     * that's gonna get updated every second.
     * @param sh non-null for sheet-style dialogs.
     * @param format Formatted string that contains a "%d" to display the remaining seconds.
     * @param duration Number of seconds that our dialog will be open.
     */
    public boolean ask(@Nullable Shell sh, MessageType mt, String format, long duration)
    {
        return ask(sh, mt, format, IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, duration);
    }

    /**
     * @param sh non-null for sheet-style dialogs
     */
    public boolean ask(Shell sh, MessageType mt, String msg, String yesLabel,
            String noLabel)
    {
        boolean sheet;
        if (sh == null) {
            sh = _sh;
            sheet = false;
        } else {
            sheet = true;
        }

        return askImpl(sh, sheet, mt, msg, yesLabel, noLabel);
    }

    /**
     * Shows a message box with {@code yesLabel}, {@code noLabel} buttons for {@code duration}
     * seconds that's gonna get updated every second.
     * @param sh non-null for sheet-style dialogs.
     * @param format Formatted string that contains a "%d" to display the remaining seconds.
     * @param duration Number of seconds until the dialog closes.
     */
    public boolean ask(@Nullable Shell sh, MessageType mt, String format, String yesLabel,
            String noLabel, long duration)
    {
        boolean sheet;
        if (sh == null) {
            sh = _sh;
            sheet = false;
        } else {
            sheet = true;
        }

        return askImpl(sh, sheet, mt, format, yesLabel, noLabel, duration);
    }

    private boolean askImpl(final Shell sh, final boolean sheet, final MessageType mt,
            final String msg, final String yesLabel, final String noLabel)
    {
        final InOutArg<Boolean> yes = new InOutArg<Boolean>(false);
        exec(new Runnable()
        {
            @Override
            public void run()
            {
                AeroFSMessageBox amb = new AeroFSMessageBox(sh, sheet, msg, mt2it(mt),
                        ButtonType.OKAY_CANCEL, yesLabel, noLabel, null, true);
                yes.set(amb.open() == IDialogConstants.OK_ID);
            }
        });
        return yes.get();
    }

    /**
     * Shows a message box with {@code yesLabel}, {@code noLabel} buttons for {@code duration}
     * seconds that's gonna get updated every second.
     * @param sh non-null for sheet-style dialogs.
     * @param format Formatted string that contains a "%d" to display the remaining seconds.
     * @param duration Number of seconds until the dialog closes.
     */
    private boolean askImpl(@Nullable final Shell sh, final boolean sheet, final MessageType mt,
            final String format, final String yesLabel, final String noLabel,
            final long duration)
    {
        final InOutArg<Boolean> yes = new InOutArg<Boolean>(false);
        exec(new Runnable()
        {
            @Override
            public void run()
            {
                final AeroFSTimedMessageBox atmb = new AeroFSTimedMessageBox(sh, sheet,
                        mt2it(mt), format, ButtonType.OKAY_CANCEL, yesLabel, noLabel, duration);
                // update periodically the label to show the remaining time.
                atmb.startTimer();
                yes.set(atmb.open() == IDialogConstants.OK_ID);
            }
        });
        return yes.get();
    }

    @Override
    public boolean ask(MessageType mt, String msg)
    {
        return askImpl(_sh, false, mt, msg, IDialogConstants.YES_LABEL,
                IDialogConstants.NO_LABEL);
    }

    @Override
    public boolean ask(MessageType mt, String msg, String yesLabel, String noLabel)
    {
        return askImpl(_sh, false, mt, msg, yesLabel, noLabel);
    }

    /**
     * Shows a message box with yes,no buttons for {@code duration} seconds
     * that's gonna get updated every second.
     * @param format Formatted string that contains a "%d" to display the remaining seconds.
     * @param duration Number of seconds until the dialog closes.
     */
    public boolean ask(MessageType mt, String format, long duration)
            throws ExNoConsole
    {
        return ask(_sh, mt, format, duration);
    }

    /**
     * Shows a message box with {@code yesLabel}, {@code noLabel} buttons for {@code duration}
     * seconds that's gonna get updated every second.
     * @param format Formatted string that contains a "%d" to display the remaining seconds.
     * @param duration Number of seconds until the dialog closes.
     */
    public boolean ask(MessageType mt, String format, String yesLabel,
            String noLabel, long duration)
            throws ExNoConsole
    {
        return askImpl(_sh, false, mt, format, yesLabel, noLabel, duration);
    }


    // where run() is called in a different thread, okay and error are
    // called within the UI thread.
    public static interface ISWTWorker {
        void run() throws Exception;
        void okay();
        void error(Exception e);
    }

    /**
     * This method calls worker.run() in a separate thread, and call either worker.okay() or error()
     * depending on whether run() throws. Avoid using this method when possible. Use safeWork()
     * instead.
     */
    public void unsafeWork(final ISWTWorker worker)
    {
        ThreadUtil.startDaemonThread("GUI worker", new Runnable()
        {
            @Override
            public void run()
            {
                Exception e1 = null;
                try {
                    worker.run();
                } catch (Exception e) {
                    e1 = e;
                }

                final Exception e2 = e1;
                asyncExec(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if (e2 == null) worker.okay();
                        else worker.error(e2);
                    }
                });
            }
        });
    }

    /**
     * Same as unsafeWork(), except that it checks whether the given widget has been disposed before
     * calling okay() or error(). Since these methods usually refressh the GUI, it's important to
     * make sure the widget to refresh is still valid before refreshing (not disposed).
     */
    public void safeWork(final Widget w, final ISWTWorker worker)
    {
        Thread thd = new Thread() {
            @Override
            public void run()
            {
                Exception e1 = null;
                try {
                    worker.run();
                } catch (Exception e) {
                    e1 = e;
                }

                final Exception e2 = e1;
                safeAsyncExec(w, new Runnable() {
                    @Override
                    public void run()
                    {
                        if (e2 == null) worker.okay();
                        else worker.error(e2);
                    }
                });
            }
        };
        thd.setDaemon(true);
        thd.start();
    }

    @Override
    public void setup_(String rtRoot) throws Exception
    {
        AbstractDlgSetup dlg = L.get().isMultiuser() ? new MultiuserDlgSetup(_sh) :
                new SingleuserDlgSetup(_sh);
        dlg.open();
        if (dlg.isCancelled()) throw new ExAborted("user canceled setup");
    }

    public void enterMainLoop_()
    {
        while (!_disp.isDisposed()) {
            if (!_disp.readAndDispatch()) _disp.sleep();
        }
    }

    @Override
    public void confirm(MessageType mt, String msg)
    {
        show(mt, msg);
    }

    @Override
    public Object addProgress(final String msg, final boolean notify)
    {
        final InOutArg<Object> ret = new InOutArg<Object>(null);
        exec(new Runnable() {
            @Override
            public void run()
            {
                if (_st != null) ret.set(_st.getProgs().addProgress(msg, notify));
            }
        });
        return ret.get();
    }

    @Override
    public void removeProgress(final Object prog)
    {
        if (prog == null) return;

        asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                if (_st != null) _st.getProgs().removeProgress(prog);
            }
        });
    }

    @Override
    public void removeAllProgresses()
    {
        asyncExec(new Runnable() {
            @Override
            public void run()
            {
                if (_st != null) _st.getProgs().removeAllProgresses();
            }
        });
    }

    @Override
    public boolean areNotificationsClickable()
    {
        return true;
    }

    @Override
    public void notify(MessageType mt, String msg)
    {
        notify(mt, msg, null);
    }

    @Override
    public void notify(MessageType mt, String msg, Runnable onClick)
    {
        notify(mt, L.PRODUCT, msg, onClick);
    }

    @Override
    public void notify(final MessageType mt, final String title, final String msg,
            final Runnable onClick)
    {
        asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                if (_st != null) _st.getBalloons().add(mt, title, msg, onClick);
            }
        });
    }

    @Override
    public boolean hasVisibleNotifications()
    {
        final InOutArg<Boolean> ret = new InOutArg<Boolean>(false);

        exec(new Runnable()
        {
            @Override
            public void run()
            {
                if (_st != null) ret.set(_st.getBalloons().hasVisibleBalloon());
            }
        });

        return ret.get();
    }

    /**
     * @param stopDaemon false if the client will call The.dm().stop() itself.
     * This is desired sometimes as the call may take time and block the UI thread
     */
    public void shutdown(final boolean stopDaemon)
    {
        exec(new Runnable() {
            @Override
            public void run() {
                if (st() != null) st().dispose();

                if (stopDaemon && UI.dm() != null) UI.dm().stopIgnoreException();
            }
        });
    }

    @Override
    public void shutdown()
    {
        shutdown(true);
    }

    @Override
    public void retypePassword()
    {
        new DlgRetypePassword(GUI.get().sh()).open();
    }

    private int _openShells;

    private final HashSet<Shell> _open = new HashSet<Shell>(); // for debugging only

    /**
     * See registerShell()
     */
    public boolean isOpen()
    {
        l.info("isOpen(): " + (_openShells != 0));
        boolean open = _openShells != 0;

        // for debugging only. TODO remove it
        exec(new Runnable() {
            @Override
            public void run()
            {
                for (Shell shell : _open) {
                    if (shell.isDisposed() || !shell.isVisible()) {
                        SVClient.logSendDefectAsync(true, "closed shells in _open: " + shell +
                                ": " + shell.isDisposed());
                    }
                }
            }
        });

        return open;
    }

    /**
     * listen to the shell's open/close event. ideally we should listen to all
     * the shells. however, it's not easy to force it (i.e. to use a custom
     * shell everywhere). we have to rely on the classes' constructors to call
     * this method
     */
    public void registerShell(final Shell shell)
    {
        // clients must call us before the shell is open or disposed
        assert !shell.isVisible();
        assert !shell.isDisposed();
        assert _openShells >= 0;

        if (_open.size() != _openShells) {
            String shs = "";
            for (Shell sh : _open) shs += " " + sh;
            SVClient.logSendDefectAsync(true, "_open != open: " + _openShells + " == " + shs);
        }

        _openShells++;
        l.info("open " + _openShells);

        if (!_open.add(shell)) {
            SVClient.logSendDefectAsync(true, "re-register shell: " + shell);
        }

        shell.addDisposeListener(new DisposeListener()
        {
            @Override
            public void widgetDisposed(DisposeEvent e)
            {
                _openShells--;
                l.info("dispose " + _openShells);

                if (!_open.remove(shell)) {
                    SVClient.logSendDefectAsync(true, "re-unregister shell: " + shell);
                }

                if (_open.size() != _openShells) {
                    String shs = "";
                    for (Shell sh : _open) shs += " " + sh;
                    SVClient.logSendDefectAsync(true,
                            "_open != open: " + _openShells + " == " + shs);
                }
            }
        });
    }

    @Override
    public void preSetupUpdateCheck_() throws Exception
    {
        new DlgPreSetupUpdateCheck(GUI.get().sh()).open();
    }
}
