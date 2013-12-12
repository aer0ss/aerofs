/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.singleuser.preferences;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.exclusion.DlgExclusion;
import com.aerofs.gui.preferences.PreferencesHelper;
import com.aerofs.gui.transfers.DlgThrottling;
import com.aerofs.gui.transport_diagnostics.DlgTransportDiagnostics;
import com.aerofs.gui.unlink.DlgUnlinkDevice;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

public class SingleuserCompPreferences extends Composite
{
    private final Label _lblId2;
    private final Label _lblId;
    private boolean _deviceIDShown;

    public SingleuserCompPreferences(Composite parent)
    {
        super(parent, SWT.NONE);

        PreferencesHelper helper = new PreferencesHelper(this);
        helper.setLayout();

        // Id row

        _lblId = new Label(this, SWT.RIGHT);
        _lblId.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblId.setText("User ID:");

        _lblId2 = new Label(this, SWT.NONE);
        _lblId2.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        _lblId2.setText(Cfg.user().getString());
        _lblId2.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseUp(MouseEvent e)
            {
                if (_deviceIDShown) {
                    _lblId.setText("User ID:");
                    _lblId2.setText(Cfg.user().getString());
                } else {
                    _lblId.setText("Computer ID:");
                    _lblId2.setText(Cfg.did().toStringFormal());
                }
                _deviceIDShown = !_deviceIDShown;
                layout(new Control[]{_lblId2, _lblId});
            }
        });

        // Device name row

        helper.createDeviceNameLabelAndText();

        // Manage devices row

        helper.createManageDevices("Manage your devices", WWW.DEVICES_URL);

        // Root anchor relocation row

        helper.createRelocationLabelAndText();

        // Show notifications row

        new Label(this, SWT.NONE);

        final Button btnNotify = GUIUtil.createButton(this, SWT.CHECK);
        btnNotify.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        btnNotify.setText("Notify me about file changes");
        btnNotify.setSelection(Cfg.db().getBoolean(Key.NOTIFY));
        btnNotify.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setCfg(Key.NOTIFY, btnNotify.getSelection());
            }
        });

        // Enable sync history row
        new Label(this, SWT.NONE);

        final Button btnHistory = GUIUtil.createButton(this, SWT.CHECK);
        btnHistory.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        btnHistory.setText(S.ENABLE_SYNC_HISTORY);
        btnHistory.setSelection(Cfg.db().getBoolean(Key.SYNC_HISTORY));
        // This button is a little complicated - we present a warning only if the
        // selection state goes from on to off. If the user clicks No, the selection state
        // is forced back to true.
        btnHistory.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (!btnHistory.getSelection()) {
                    if (GUI.get().ask(getShell(), MessageType.WARN, S.SYNC_HISTORY_CONFIRM)) {
                        setCfg(Key.SYNC_HISTORY, false);
                    } else {
                        btnHistory.setSelection(true);
                    }
                } else {
                    setCfg(Key.SYNC_HISTORY, true);
                }

                try {
                    UIGlobals.ritual().reloadConfig();
                } catch (Exception e1) {
                    GUI.get().show(getShell(), MessageType.ERROR,
                            "Couldn't update Sync History " + ErrorMessages.e2msgDeprecated(e1) + ".");
                }
            }
        });

        // Spinner row

        helper.createSpinner();

        // Separator row

        Label label_1 = new Label(this, SWT.SEPARATOR | SWT.HORIZONTAL);
                label_1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1));

        // Empty row

        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);
        new Label(this, SWT.NONE);

        // Buttons row

        new Label(this, SWT.NONE);
        Composite btnsComposite = new Composite(this, SWT.NONE);
        createButtons(btnsComposite);
        new Label(this, SWT.NONE);

        helper.registerShellListeners();
    }

    private void createButtons(Composite composite)
    {
        GridLayout gl_composite_1 = new GridLayout(1, false);
        gl_composite_1.marginLeft = OSUtil.isOSX() ? -6 : 0;
        gl_composite_1.verticalSpacing = OSUtil.isOSX() ? -2 : gl_composite_1.verticalSpacing;
        gl_composite_1.marginWidth = 0;
        gl_composite_1.marginHeight = 0;
        composite.setLayout(gl_composite_1);
        composite.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

        Button btnSelectiveSync = GUIUtil.createButton(composite, SWT.NONE);
        btnSelectiveSync.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        btnSelectiveSync.setText("Selective Sync...");
        btnSelectiveSync.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new DlgExclusion(getShell()).openDialog();
            }
        });

        Button btnBandwidth = GUIUtil.createButton(composite, SWT.NONE);
        btnBandwidth.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        btnBandwidth.setText("Limit Bandwidth...");
        btnBandwidth.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                new DlgThrottling(getShell(), true).openDialog();
            }
        });

        Button btnTransportDiagnostic = GUIUtil.createButton(composite, SWT.NONE);
        btnTransportDiagnostic.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        btnTransportDiagnostic.setText(S.TXT_TRANSPORT_DIAGNOSTICS_TITLE);
        btnTransportDiagnostic.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                new DlgTransportDiagnostics(getShell()).openDialog();
            }
        });

        new Label(composite, SWT.NONE);

        Button button = GUIUtil.createButton(composite, SWT.NONE);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        button.setText(S.UNLINK_THIS_COMPUTER);
        button.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                new DlgUnlinkDevice(getShell(), true).openDialog();
            }
        });
    }

    private void setCfg(CfgDatabase.Key key, Boolean value)
    {
        try {
            Cfg.db().set(key, value);
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.ERROR, "Couldn't change settings "
                    + ErrorMessages.e2msgDeprecated(e));
        }
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }
}
