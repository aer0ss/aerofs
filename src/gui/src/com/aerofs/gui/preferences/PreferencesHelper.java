/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.preferences;

import com.aerofs.base.Loggers;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Sp.GetUserPreferencesReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.widgets.Link;
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

public class PreferencesHelper
{
    private final static Logger l = Loggers.getLogger(PreferencesHelper.class);

    private final Composite _comp;

    private Text _txtDeviceName;
    private Text _txtRootAnchor;

    private CompSpin _compSpin;

    private boolean _asyncInited;
    private @Nullable String _deviceName;

    public PreferencesHelper(Composite comp)
    {
       _comp = comp;
    }

    public void selectAndMoveRootAnchor(final Text txtRootAnchor)
    {
        // Have to re-open the directory dialog in a separate stack, since doing it in the same
        // stack would cause strange SWT crashes on OSX :/
        GUI.get().safeAsyncExec(_comp, new Runnable() {
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
            GUI.get().show(_comp.getShell(), MessageType.WARN, e.getMessage() +
                    ". Please select a different folder.");
            return true;
        }

        if (!GUI.get().ask(_comp.getShell(), MessageType.QUESTION,
                "Are you sure you want to move the " + S.ROOT_ANCHOR +
                        " and its content from:\n\n" + pathOld + "\n\nto:\n\n" +
                        pathNew + "?")) {
            return false;
        }

        DlgMoveRootAnchor dlg = new DlgMoveRootAnchor(_comp.getShell(), true, pathNew);

        Boolean success = (Boolean) dlg.openDialog();

        return success != null && success;
    }

    /**
     * @return Path of new root anchor
     */
    private String getRootAnchorPathFromDirectoryDialog()
    {
        DirectoryDialog dd = new DirectoryDialog(_comp.getShell(), SWT.SHEET);
        dd.setMessage("Select " + S.ROOT_ANCHOR);
        return dd.open();
    }

    public void createRelocationLabelAndText()
    {
        Label lblRootAnchor = new Label(_comp, SWT.NONE);
        lblRootAnchor.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblRootAnchor.setText(S.ROOT_ANCHOR);

        _txtRootAnchor = new Text(_comp, SWT.BORDER | SWT.READ_ONLY);
        _txtRootAnchor.setLayoutData(getTextFieldGridData());

        Button btnMoveRoot = GUIUtil.createButton(_comp, SWT.NONE);
        btnMoveRoot.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
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

        new Label(_comp, SWT.NONE);
    }

    public void createManageDevices(String text, final String url)
    {
        new Label(_comp, SWT.NONE);

        Link link = new Link(_comp, SWT.NONE);
        link.setText("<a>" + text + "</a>");
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
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
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
                    SPBlockingClient.Factory fact = new SPBlockingClient.Factory();
                    SPBlockingClient sp = fact.create_(Cfg.user());
                    sp.signInRemote();
                    r = sp.getUserPreferences(Cfg.did().toPB());
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
                            GUI.get().show(_comp.getShell(), MessageType.ERROR,
                                    ErrorMessages.e2msgDeprecated(eFinal));
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
                    SPBlockingClient.Factory fact = new SPBlockingClient.Factory();
                    SPBlockingClient sp = fact.create_(Cfg.user());
                    sp.signInRemote();
                    sp.setUserPreferences(Cfg.user().getString(), null, null, Cfg.did().toPB(),
                            deviceNameToSend);
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
                            GUI.get()
                                    .show(_comp.getShell(), MessageType.ERROR,
                                            "Couldn't update the name " + ErrorMessages.e2msgDeprecated(
                                                    eFinal));
                        } else {
                            _deviceName = deviceName;
                            if (selectAll != null) selectAll.selectAll();
                        }
                    }
                });
            }
        });
    }

    public void createSpinner()
    {
        _compSpin = new CompSpin(_comp, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
    }

    public void registerShellListeners()
    {
        _comp.getShell().addListener(SWT.Show, new Listener()
        {
            @Override
            public void handleEvent(Event arg0)
            {
                initAsync();
            }
        });

        // Save settings on close
        _comp.getShell().addShellListener(new ShellAdapter()
        {
            @Override
            public void shellClosed(ShellEvent shellEvent)
            {
                updateDeviceName(null);
            }
        });
    }

    public void setLayout()
    {
        GridLayout gridLayout = new GridLayout(3, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = 10;
        _comp.setLayout(gridLayout);
    }
}
