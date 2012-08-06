package com.aerofs.gui.setup;

import com.aerofs.proto.ControllerNotifications.Type;
import com.aerofs.proto.ControllerNotifications.UpdateNotification;
import com.aerofs.proto.ControllerNotifications.UpdateNotification.Status;
import com.aerofs.ui.IUINotificationListener;
import com.google.protobuf.GeneratedMessageLite;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
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
import com.aerofs.lib.Util;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.custom.StackLayout;

public class DlgPreSetupUpdateCheck extends Shell
{
    private final ProgressBar _prog;
    private final Composite _composite;
    private final Label _lblDownloading;
    private final Label _lblChecking;
    private final StackLayout _sl;
    private boolean _firstOngoing = true;

    public DlgPreSetupUpdateCheck(Shell shell)
    {
        super(shell, SWT.DIALOG_TRIM | GUIUtil.alwaysOnTop());
        setText("Setup " + S.PRODUCT);

        GridLayout gridLayout = new GridLayout(1, false);
        // same as GUIDownloader
        gridLayout.horizontalSpacing = 8;
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = GUIParam.MARGIN;
        setLayout(gridLayout);

        _sl = new StackLayout();
        _composite = new Composite(this, SWT.NONE);
        _composite.setLayout(_sl);

        _lblDownloading = new Label(_composite, SWT.NONE);
        _lblDownloading.setText("Downloading updates. " + S.PRODUCT +
                " will automatically restart...  ");

        _lblChecking = new Label(_composite, SWT.NONE);
        _lblChecking.setText(S.CHECKING_FOR_DINOSAURS);

        _sl.topControl = _lblChecking;

        _prog = new ProgressBar(this, SWT.NONE);
        GridData gdProgressBar = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        _prog.setLayoutData(gdProgressBar);

        Button button = new Button(this, SWT.NONE);
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                cancel();
            }
        });
        button.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        button.setText(IDialogConstants.CANCEL_LABEL);

        addListener(SWT.Traverse, new Listener() {
            @Override
            public void handleEvent(Event event)
            {
                if (event.detail == SWT.TRAVERSE_ESCAPE) cancel();
            }
        });

        UI.notifier().addListener(Type.UPDATE_NOTIFICATION, _updateListener);

        addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent arg0)
            {
                UI.notifier().removeListener(Type.UPDATE_NOTIFICATION, _updateListener);
            }
        });
    }

    private void cancel()
    {
        Util.l(DlgPreSetupUpdateCheck.this).warn("user canceled");
        System.exit(1);
    }

    public void open()
    {
        layout();
        pack();
        GUIUtil.centerShell(this);
        super.open();

        UI.updater().checkForUpdate(true);

        while (!isDisposed()) {
            if (!getDisplay().readAndDispatch()) getDisplay().sleep();
        }
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }

    private final IUINotificationListener _updateListener = new IUINotificationListener() {
        @Override
        public void onNotificationReceived(GeneratedMessageLite notification)
        {
            UpdateNotification n = (UpdateNotification)notification;
            setUpdateStatus(n.getStatus(), n.hasProgress() ? n.getProgress() : -1);
        }
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
            System.exit(1);
            break;
        case APPLY:
            UI.updater().execUpdateFromMenu();
            break;
        default:
            assert us == Status.NONE;
        }
    }
}
