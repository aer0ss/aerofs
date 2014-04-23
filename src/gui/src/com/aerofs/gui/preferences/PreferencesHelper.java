/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.preferences;

import com.aerofs.base.Loggers;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.transfers.DlgThrottling;
import com.aerofs.labeling.L;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgRestService;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Sp.GetUserPreferencesReply;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.update.Updater;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import javax.annotation.Nullable;

import java.io.IOException;

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
        GUI.get().safeAsyncExec(_comp, new Runnable()
        {
            @Override
            public void run()
            {
                String root = getRootAnchorPathFromDirectoryDialog();
                if (root == null) return; //User hit cancel
                if (moveRootAnchor(root)) {
                    txtRootAnchor.setText(Cfg.absDefaultRootAnchor());
                } else {
                    selectAndMoveRootAnchor(txtRootAnchor);
                }
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
        Label lblRootAnchor = new Label(_comp, SWT.NONE);
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
        Label lblDeviceName = new Label(_comp, SWT.NONE);
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
        new Label(_comp, SWT.NONE);

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

        new Label(_comp, SWT.NONE);
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
                            .getUserPreferences(Cfg.did().toPB());
                } catch (ExBadCredential ebc) {
                    l.warn("ExBadCredential", LogUtil.suppress(ebc));
                } catch (Exception e2) {
                    l.warn(Util.e(e2));
                    e = e2;
                }

                final GetUserPreferencesReply reply = r;
                final Exception eFinal = e;
                GUI.get().safeAsyncExec(_comp, new Runnable() {
                    @Override
                    public void run()
                    {
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
                    }
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

        ThreadUtil.startDaemonThread("update-names", new Runnable()
        {
            @Override
            public void run()
            {
                Exception e;
                try {
                    newMutualAuthClientFactory()
                            .create()
                            .signInRemote()
                            .setUserPreferences(Cfg.user().getString(), null, null,
                                    Cfg.did().toPB(), deviceNameToSend);
                    e = null;
                } catch (Exception e2) {
                    e = e2;
                }

                final Exception eFinal = e;
                GUI.get().safeAsyncExec(_comp, new Runnable()
                {
                    @Override
                    public void run()
                    {
                        _compSpin.stop();

                        if (eFinal != null) {
                            ErrorMessages.show(_shell, eFinal, "Sorry, " + L.product() +
                                    " couldn't update the computer name.");
                        } else {
                            _deviceName = deviceName;
                            if (selectAll != null) selectAll.selectAll();
                        }
                    }
                });
            }
        });
    }

    /**
     * Creates a composite containing a button with the text {@paramref}, which opens the dialog
     * {@paramref dialog} when clicked.
     */
    public Control createButtonContainer(Composite parent, String text, final AeroFSDialog dialog)
    {
        Control control;
        Button button;

        new Label(parent, SWT.NONE).setLayoutData(new GridData(GUIParam.MARGIN, SWT.DEFAULT));

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

        new Label(parent, SWT.NONE).setLayoutData(new GridData(GUIParam.MARGIN, SWT.DEFAULT));

        button.setText(text);
        button.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                dialog.openDialog();
            }
        });

        return control;
    }

    public void createAdvancedButton(Composite parent, AeroFSDialog dialog)
    {
        createButtonContainer(parent, "Advanced...", dialog)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    public void createLimitBandwidthButton(Shell shell)
    {
        createButtonContainer(shell, "Limit Bandwidth...", new DlgThrottling(shell, true))
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
    }

    public void setCfg(CfgDatabase.Key key, Boolean value)
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
        _shell.addListener(SWT.Show, new Listener()
        {
            @Override
            public void handleEvent(Event arg0)
            {
                initAsync();
            }
        });

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

    public void setLayout(Composite composite)
    {
        GridLayout gridLayout = new GridLayout(3, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = 10;
        composite.setLayout(gridLayout);
    }

    public void setLayoutForAdvanced(Composite composite)
    {
        GridLayout layout = new GridLayout(5, false);
        layout.marginWidth = 0;
        layout.marginHeight = GUIParam.MARGIN;
        layout.verticalSpacing = GUIParam.VERTICAL_SPACING;
        composite.setLayout(layout);
    }

    public void createSyncHistory(Composite parent)
    {
        new Label(parent, SWT.NONE).setLayoutData(new GridData(GUIParam.MARGIN, SWT.DEFAULT));

        final Button btnHistory = GUIUtil.createButton(parent, SWT.CHECK);
        btnHistory.setText(S.ENABLE_SYNC_HISTORY);
        btnHistory.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        btnHistory.setSelection(Cfg.db().getBoolean(Key.SYNC_HISTORY));
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
                        setCfg(Key.SYNC_HISTORY, false);
                    } else {
                        btnHistory.setSelection(true);
                    }
                } else {
                    setCfg(Key.SYNC_HISTORY, true);
                }

                try {
                    UIGlobals.ritual().reloadConfig();
                } catch (Exception ex) {
                    showErrorMessage(ex);
                }
            }
        });

        new Label(parent, SWT.NONE).setLayoutData(new GridData(GUIParam.MARGIN, SWT.DEFAULT));
    }

    public void createAPIAccess(Composite parent)
    {
        new Label(parent, SWT.NONE).setLayoutData(new GridData(GUIParam.MARGIN, SWT.DEFAULT));

        final Button btnAPIAccess = GUIUtil.createButton(parent, SWT.CHECK);

        btnAPIAccess.setText("Enable API access");
        btnAPIAccess.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
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
                setCfg(Key.REST_SERVICE, btnAPIAccess.getSelection());

                try {
                    UIGlobals.dm().stop();
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

        new Label(parent, SWT.NONE).setLayoutData(new GridData(GUIParam.MARGIN, SWT.DEFAULT));
    }

    private static final int STABLE_INDEX = 0;
    private static final int CANARY_INDEX = 1;

    private static boolean isCanary()
    {
        return Updater.CANARY_FLAG_FILE.exists();
    }

    public void createCanaryMode(Composite parent)
    {
        // Don't show the Canary option for private deployment
        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT) return;

        new Label(parent, SWT.NONE).setLayoutData(new GridData(GUIParam.MARGIN, SWT.DEFAULT));

        new Label(parent, SWT.NONE).setText("Release channel:");

        final Combo comboCanary = new Combo(parent, SWT.READ_ONLY);
        comboCanary.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        comboCanary.add("Stable");
        comboCanary.add("Canary");

        comboCanary.select(isCanary() ? CANARY_INDEX : STABLE_INDEX);
        comboCanary.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                onCanarySelection(comboCanary);
            }
        });

        Link lnkCanary = new Link(parent, SWT.NONE);
        lnkCanary.setText("<a>What is this?</a>");
        lnkCanary.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        lnkCanary.setVisible(false);
        //lnkCanary.addSelectionListener(GUIUtil.createUrlLaunchListener(S.URL_API_ACCESS));

        new Label(parent, SWT.NONE).setLayoutData(new GridData(GUIParam.MARGIN, SWT.DEFAULT));
    }

    private void onCanarySelection(Combo comboCanary)
    {
        boolean toCanary = comboCanary.getSelectionIndex() == CANARY_INDEX;
        // No change in selection.
        if (toCanary == isCanary()) return;

        try {
            if (toCanary) {
                String message = "Your " + L.product() + " may restart shortly to update to the" +
                        " Canary release.";
                if (GUI.get().ask(_shell, MessageType.INFO, message, "Continue", "Cancel")) {
                    FileUtil.createNewFile(Updater.CANARY_FLAG_FILE);
                    UIGlobals.updater().checkForUpdate(true);
                } else {
                    comboCanary.select(STABLE_INDEX);
                }

            } else {
                String message = L.product() + " will update to the Stable release when the" +
                        " Stable release reaches version " + Cfg.ver() + ".\n\n" +
                        "Warning: do not attempt to downgrade " + L.product() + " to a previous" +
                        " version. Doing so will cause unexpected behavior and potential data" +
                        " loss.";
                GUI.get().show(_shell, MessageType.INFO, message);
                FileUtil.deleteOrThrowIfExist(Updater.CANARY_FLAG_FILE);
            }
        } catch (IOException e) {
            // Canary users are assumed to be tech savvy. So show them technical details.
            ErrorMessages.show(_shell, e, "",
                    new ErrorMessage(IOException.class, "Failed to toggle the release channel: " +
                            e.getLocalizedMessage()));
            comboCanary.select(toCanary ? STABLE_INDEX : CANARY_INDEX);
        }
    }

    // use invisible separator to increase spacing between widgets
    public void createSeparator(Composite parent, boolean visible)
    {
        Label separator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        // spans 4 columns because it's the most number of columns for the layouts we use
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 5, 1));
        separator.setVisible(visible);
    }
}
