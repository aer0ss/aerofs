package com.aerofs.gui.setup;

import com.aerofs.base.Loggers;
import com.aerofs.controller.IViewNotifier.Type;
import com.aerofs.labeling.L;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.ui.IUINotificationListener;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.update.Updater.Status;
import com.aerofs.ui.update.Updater.UpdaterNotification;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.custom.StackLayout;
import org.slf4j.Logger;

import static com.aerofs.gui.GUIUtil.createLabel;

public class DlgPreSetupUpdateCheck extends Shell
{
    private static final Logger l = Loggers.getLogger(DlgPreSetupUpdateCheck.class);

    private final ProgressBar _prog;
    private final Composite _composite;
    private final Label _lblDownloading;
    private final StackLayout _sl;
    private boolean _firstOngoing = true;

    public DlgPreSetupUpdateCheck(Shell shell)
    {
        super(shell, GUIUtil.createShellStyle(false, false, true));
        setText("Setup " + L.product());

        GridLayout gridLayout = new GridLayout(1, false);
        // same as GUIDownloader
        gridLayout.horizontalSpacing = 8;
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = GUIParam.MARGIN;
        setLayout(gridLayout);

        _sl = new StackLayout();
        _composite = new Composite(this, SWT.NONE);
        _composite.setLayout(_sl);

        _lblDownloading = createLabel(_composite, SWT.NONE);
        _lblDownloading.setText("Downloading updates. " + L.product() +
                " will automatically restart...  ");

        Label lblChecking = createLabel(_composite, SWT.NONE);
        lblChecking.setText(S.CHECKING_FOR_DINOSAURS);

        _sl.topControl = lblChecking;

        _prog = new ProgressBar(this, SWT.NONE);
        GridData gdProgressBar = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        _prog.setLayoutData(gdProgressBar);

        Button button = GUIUtil.createButton(this, SWT.NONE);
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                cancel();
            }
        });
        button.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        button.setText(IDialogConstants.CANCEL_LABEL);

        addListener(SWT.Traverse, event -> {
            if (event.detail == SWT.TRAVERSE_ESCAPE) cancel();
        });

        UIGlobals.notifier().addListener(Type.UPDATE, _updateListener);

        addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent arg0)
            {
                UIGlobals.notifier().removeListener(Type.UPDATE, _updateListener);
            }
        });
    }

    private void cancel()
    {
        l.warn("user canceled");
        ExitCode.FAIL_TO_LAUNCH.exit("user canceled");
    }

    @Override
    public void open()
    {
        layout();
        pack();
        GUIUtil.centerShell(this);
        super.open();

        UIGlobals.updater().checkForUpdate(true);

        while (!isDisposed()) {
            if (!getDisplay().readAndDispatch()) getDisplay().sleep();
        }
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }

    private final IUINotificationListener _updateListener = notification -> {
        UpdaterNotification n = (UpdaterNotification)notification;
        setUpdateStatus(n.status, n.progress);
    };

    private void setUpdateStatus(Status us, int percent)
    {
        if (isDisposed()) return;

        switch (us) {
        case LATEST:
            close();
            break;
        case ONGOING:
            if (percent > 0) {
                _prog.setSelection(percent);
                if (_firstOngoing) {
                    _firstOngoing = false;
                    _sl.topControl = _lblDownloading;
                    _composite.layout();
                }
            }
            break;
        case ERROR:
            GUI.get().show(this, MessageType.WARN, S.PRE_SETUP_UPDATE_CHECK_FAILED);
            ExitCode.FAIL_TO_LAUNCH.exit();
            break;
        case APPLY:
            UIGlobals.updater().execUpdateFromMenu();
            break;
        case NONE:
            break;
        case DISABLED:
            l.warn("ignored: update disabled; received unexpected update status: {}.", us);
            break;
        default:
            l.warn("ignored: received unrecognized update status: {}.", us);
            break;
        }
    }
}
