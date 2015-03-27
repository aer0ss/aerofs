/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.preferences;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.gui.*;
import com.aerofs.gui.exclusion.DlgExclusion;
import com.aerofs.gui.unlink.DlgUnlinkDevice;
import com.aerofs.labeling.L;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgKey;
import com.aerofs.lib.cfg.CfgRestService;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Sp.GetUserPreferencesReply;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.lib.cfg.CfgDatabase.REST_SERVICE;
import static com.aerofs.lib.cfg.CfgDatabase.SYNC_HISTORY;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class PreferencesHelper
{
    private final static Logger l = Loggers.getLogger(PreferencesHelper.class);

    private final Shell _shell;
    private final Composite _comp;

    private Text _txtDeviceName;
    private Text _txtRootAnchor;

    private CompSpin _compSpin;

    private boolean _asyncInited;
    private @Nullable String _deviceName;

    public PreferencesHelper(Composite comp)
    {
        _comp = comp;
        _shell = comp.getShell();
    }

    public void selectAndMoveRootAnchor(final Text txtRootAnchor)
    {
        // Have to re-open the directory dialog in a separate stack, since doing it in the same
        // stack would cause strange SWT crashes on OSX :/
        GUI.get().safeAsyncExec(_comp, () -> {
            String root = getRootAnchorPathFromDirectoryDialog();
            if (root == null) return; //User hit cancel
            if (moveRootAnchor(root)) {
                txtRootAnchor.setText(Cfg.absDefaultRootAnchor());
            } else {
                selectAndMoveRootAnchor(txtRootAnchor);
            }
        });
    }

    /**
     * @return whether we were successful
     */
    private boolean moveRootAnchor(String rootParent)
    {
        String pathOld = Cfg.absDefaultRootAnchor();
        String pathNew = RootAnchorUtil.adjustRootAnchor(rootParent, null);

        try {
            RootAnchorUtil.checkNewRootAnchor(pathOld, pathNew);
        } catch (Exception e) {
            GUI.get().show(_shell, MessageType.WARN, e.getMessage() +
                    ". Please select a different folder.");
            return true;
        }

        if (!GUI.get().ask(_shell, MessageType.QUESTION,
                "Are you sure you want to move the " + S.ROOT_ANCHOR +
                        " and its content from:\n\n" + pathOld + "\n\nto:\n\n" +
                        pathNew + "?")) {
            return false;
        }

        DlgMoveRootAnchor dlg = new DlgMoveRootAnchor(_shell, true, pathNew);

        Boolean success = (Boolean) dlg.openDialog();

        return success != null && success;
    }

    /**
     * @return Path of new root anchor
     */
    private String getRootAnchorPathFromDirectoryDialog()
    {
        DirectoryDialog dd = new DirectoryDialog(_shell, SWT.SHEET);
        dd.setMessage("Select " + S.ROOT_ANCHOR);
        return dd.open();
    }

    public void createRelocationLabelAndText()
    {
        Label lblRootAnchor = createLabel(_comp, SWT.NONE);
        lblRootAnchor.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblRootAnchor.setText(S.ROOT_ANCHOR + ':');

        _txtRootAnchor = new Text(_comp, SWT.BORDER | SWT.READ_ONLY);
        _txtRootAnchor.setLayoutData(getTextFieldGridData());

        Button btnMoveRoot = GUIUtil.createButton(_comp, SWT.NONE);
        // the button is widen to create room so that when the label is switched to show device ID,
        // there would be enough room.
        GridData buttonLayoutData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        buttonLayoutData.widthHint = 100;
        btnMoveRoot.setLayoutData(buttonLayoutData);
        btnMoveRoot.setText("Move...");
        btnMoveRoot.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                selectAndMoveRootAnchor(_txtRootAnchor);
            }
        });
    }

    public void createDeviceNameLabelAndText()
    {
        Label lblDeviceName = createLabel(_comp, SWT.NONE);
        lblDeviceName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblDeviceName.setText("Computer name:");

        _txtDeviceName = new Text(_comp, SWT.BORDER);
        _txtDeviceName.setLayoutData(getTextFieldGridData());
        _txtDeviceName.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent focusEvent)
            {
                updateDeviceName(_txtDeviceName);
            }
        });

        _compSpin = new CompSpin(_comp, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
    }

    public void createManageDevices(String text, final String url)
    {
        createLabel(_comp, SWT.NONE);

        Link link = new Link(_comp, SWT.NONE);
        link.setText("<a>" + text + "</a>");
        link.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        link.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                GUIUtil.launch(url);
            }
        });

        createLabel(_comp, SWT.NONE);
    }

    private GridData getTextFieldGridData()
    {
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 120;
        return gd;
    }

    public void initAsync()
    {
        // set field values on SHOW instead of the ctor to avoid the
        // dialog stretched.
        _txtRootAnchor.setText(Cfg.absDefaultRootAnchor());

        _txtDeviceName.setEditable(false);

        _compSpin.start();

        // TODO (GS): This code would be much nicer using SP non-blocking client, but first we need
        // to make SPClientHandler.doRPC() asynchronous (using Netty)
        Thread thd = new Thread() {
            @Override
            public void run()
            {
                Exception e = null;
                GetUserPreferencesReply r = null;
                try {
                    r = newMutualAuthClientFactory()
                            .create()
                            .signInRemote()
                            .getUserPreferences(BaseUtil.toPB(Cfg.did()));
                } catch (ExBadCredential ebc) {
                    l.warn("ExBadCredential", LogUtil.suppress(ebc));
                } catch (Exception e2) {
                    l.warn(Util.e(e2));
                    e = e2;
                }

                final GetUserPreferencesReply reply = r;
                final Exception eFinal = e;
                GUI.get().safeAsyncExec(_comp, () -> {
                    if (eFinal != null) {
                        ErrorMessages.show(_shell, eFinal, "Sorry, " + L.product() +
                                " couldn't get the computer name.");
                    } else if (reply != null) {
                        _txtDeviceName.setText(reply.getDeviceName());
                        _txtDeviceName.setEditable(true);

                        _deviceName = _txtDeviceName.getText();

                        _asyncInited = true;
                    }
                    _compSpin.stop();
                });
            }
        };
        thd.setDaemon(true);
        thd.start();
    }

    private void updateDeviceName(@Nullable final Text selectAll)
    {
        // the user name is updated before we successfully fetched the
        // profile. ignore the request in the case.
        if (!_asyncInited) return;

        final String deviceName = _txtDeviceName.getText();
        final String deviceNameToSend = deviceName.equals(_deviceName) ? null : deviceName;

        if (deviceNameToSend == null) return;

        _compSpin.start();

        ThreadUtil.startDaemonThread("gui-pref", () -> {
            Exception e;
            try {
                newMutualAuthClientFactory()
                        .create()
                        .signInRemote()
                        .setUserPreferences(Cfg.user().getString(), null, null,
                                BaseUtil.toPB(Cfg.did()), deviceNameToSend);
                e = null;
            } catch (Exception e2) {
                e = e2;
            }

            final Exception eFinal = e;
            GUI.get().safeAsyncExec(_comp, () -> {
                _compSpin.stop();

                if (eFinal != null) {
                    ErrorMessages.show(_shell, eFinal, "Sorry, " + L.product() +
                            " couldn't update the computer name.");
                } else {
                    _deviceName = deviceName;
                    if (selectAll != null) selectAll.selectAll();
                }
            });
        });
    }

    /**
     * Creates a composite containing a button with the {@paramref label}, which opens the dialog
     * {@paramref dialog} when clicked.
     */
    public void createButtonContainerWithSpacer(Composite parent, String label,
            final AeroFSDialog dialog, int colSpan)
    {
        createSpacer(parent);
        createButtonContainer(parent, label, dialog, colSpan, SWT.FILL);
        createSpacer(parent);
    }

    /**
     * @param horizontalAlignment one of SWT.CENTER, LEFT, RIGHT, FILL
     */
    private Button createButtonContainer(Composite parent, String label,
            @Nullable final AeroFSDialog dialog, int colSpan, int horizontalAlignment)
    {
        Control control;
        Button button;
        if (OSUtil.isOSX()) {
            Composite container = new Composite(parent, SWT.NONE);
            FillLayout layout = new FillLayout();
            // we use -6 instead of -4 because we'll be lining up against text boxes
            layout.marginWidth = -6;
            layout.marginHeight = -4;
            container.setLayout(layout);

            button = GUIUtil.createButton(container, SWT.PUSH);
            control = container;
        } else {
            button = GUIUtil.createButton(parent, SWT.PUSH);
            control = button;
        }

        button.setText(label);

        if (dialog != null) {
            button.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    dialog.openDialog();
                }
            });
        }

        control.setLayoutData(new GridData(horizontalAlignment, SWT.CENTER, true, false,
                colSpan, 1));

        return button;
    }

    public void createAdvancedButton(Composite parent, AeroFSDialog dialog)
    {
        createButtonContainerWithSpacer(parent, "Advanced...", dialog, 1);
    }

    public void createSelectiveSyncButton(Composite parent)
    {
        createButtonContainerWithSpacer(parent, "Selective Sync...",
                new DlgExclusion(parent.getShell()), 2);
    }

    public void createUnlinkButton(Composite parent)
    {
        createSeparator(parent, true);

        createButtonContainerWithSpacer(parent, S.UNLINK_THIS_COMPUTER,
                new DlgUnlinkDevice(parent.getShell(), true), 2);
    }

    public void setCfg(CfgKey key, Boolean value)
    {
        try {
            Cfg.db().set(key, value);
        } catch (Exception e) {
            showErrorMessage(e);
        }
    }

    private void showErrorMessage(Exception e)
    {
        ErrorMessages.show(_shell, e, "Sorry, " + L.product() + " couldn't update settings.");
    }

    public void registerShellListeners()
    {
        _shell.addListener(SWT.Show, e -> initAsync());

        // Save settings on close
        _shell.addShellListener(new ShellAdapter()
        {
            @Override
            public void shellClosed(ShellEvent shellEvent)
            {
                updateDeviceName(null);
            }
        });
    }

    public static void setLayout(Composite composite)
    {
        GridLayout gridLayout = new GridLayout(3, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = 10;
        composite.setLayout(gridLayout);
    }

    public static void setLayoutForAdvanced(Composite composite)
    {
        GridLayout layout = new GridLayout(4, false);
        layout.marginWidth = 0;
        layout.marginHeight = GUIParam.MARGIN;
        layout.verticalSpacing = GUIParam.VERTICAL_SPACING;
        composite.setLayout(layout);
    }

    static private void createSpacer(Composite parent)
    {
        createLabel(parent, SWT.NONE).setLayoutData(new GridData(GUIParam.MARGIN, SWT.DEFAULT));
    }

    public void createSyncHistory(Composite parent)
    {
        createSpacer(parent);

        final Button btnHistory = GUIUtil.createButton(parent, SWT.CHECK);
        btnHistory.setText(S.ENABLE_SYNC_HISTORY);
        btnHistory.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        btnHistory.setSelection(Cfg.db().getBoolean(SYNC_HISTORY));
        // This button is a little complicated - we present a warning only if the
        // selection state goes from on to off. If the user clicks No, the selection state
        // is forced back to true.
        btnHistory.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (!btnHistory.getSelection()) {
                    if (GUI.get().ask(_shell, MessageType.WARN, S.SYNC_HISTORY_CONFIRM)) {
                        setCfg(SYNC_HISTORY, false);
                    } else {
                        btnHistory.setSelection(true);
                    }
                } else {
                    setCfg(SYNC_HISTORY, true);
                }

                try {
                    UIGlobals.ritual().reloadConfig();
                } catch (Exception ex) {
                    showErrorMessage(ex);
                }
            }
        });

        createSpacer(parent);
    }

    public void createAPIAccess(Composite parent)
    {
        createSpacer(parent);

        final Button btnAPIAccess = GUIUtil.createButton(parent, SWT.CHECK);

        btnAPIAccess.setText("Enable API access");
        btnAPIAccess.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        btnAPIAccess.setSelection(new CfgRestService().isEnabled());
        btnAPIAccess.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                Loggers.getLogger(PreferencesHelper.class).error("Widget selected!");

                String message = L.product() + " needs to disconnect from the network to apply " +
                        "this change. Ongoing transfers will be paused and resumed. Do you want " +
                        "to continue?";
                if (!GUI.get().ask(_shell, MessageType.WARN, message, "Continue", "Cancel")) {
                    btnAPIAccess.setSelection(!btnAPIAccess.getSelection());
                    return;
                }

                // if we made it this far, that means we should carry out the action
                setCfg(REST_SERVICE, btnAPIAccess.getSelection());

                try {
                    // the problem with calling stop that the daemon is not restarted if there are
                    // any exceptions.
                    UIGlobals.dm().stopIgnoreException();
                    UIGlobals.dm().start();
                } catch (Exception ex) {
                    String message2 = L.product() + " couldn't start the background service after " +
                            "applying these changes. Please restart " + L.product();
                    ErrorMessages.show(_shell, ex, "Unused",
                            // this is done so we don't append "Please try again later."
                            new ErrorMessage(ex.getClass(), message2));
                }
            }
        });

        Link lnkWebAccess = new Link(parent, SWT.NONE);
        lnkWebAccess.setText("<a>What is this?</a>");
        lnkWebAccess.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        lnkWebAccess.addSelectionListener(GUIUtil.createUrlLaunchListener(S.URL_API_ACCESS));

        createSpacer(parent);
    }

    // use invisible separator to increase spacing between widgets
    static public void createSeparator(Composite parent, boolean visible)
    {
        Label separator = createLabel(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        // spans 4 columns because it's the most number of columns for the layouts we use
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
        separator.setVisible(visible);
    }
}
