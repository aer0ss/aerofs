/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.singleuser.preferences;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.preferences.PreferencesHelper;
import static com.aerofs.gui.preferences.PreferencesHelper.*;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class SingleuserCompPreferences extends Composite
{
    public SingleuserCompPreferences(Composite parent)
    {
        super(parent, SWT.NONE);

        PreferencesHelper helper = new PreferencesHelper(this);
        PreferencesHelper.setLayout(this);

        createUserIDRow(this);
        helper.createDeviceNameLabelAndText();
        helper.createManageDevices("Manage your devices", WWW.DEVICES_URL);
        helper.createRelocationLabelAndText();
        createNotificationsRow(this, helper);
        helper.createAdvancedButton(this, new AdvancedDialog(getShell()));

        helper.registerShellListeners();
    }

    private void createUserIDRow(Composite parent)
    {
        final Label lblUserID = new Label(parent, SWT.RIGHT);
        lblUserID.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        lblUserID.setText("User ID:");

        final Label lblUserIDValue = new Label(parent, SWT.NONE);
        lblUserIDValue.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
        lblUserIDValue.setText(Cfg.user().getString());
        lblUserIDValue.addMouseListener(new MouseAdapter()
        {
            private boolean _deviceIDShown;

            @Override
            public void mouseUp(MouseEvent e)
            {
                _deviceIDShown = !_deviceIDShown;
                if (_deviceIDShown) {
                    lblUserID.setText("Computer ID:");
                    lblUserIDValue.setText(Cfg.did().toStringFormal());
                } else {
                    lblUserID.setText("User ID:");
                    lblUserIDValue.setText(Cfg.user().getString());
                }
                layout(new Control[]{lblUserID, lblUserIDValue});
            }
        });
    }

    private void createNotificationsRow(Composite parent, final PreferencesHelper helper)
    {
        new Label(parent, SWT.NONE);
        final Button btnNotify = GUIUtil.createButton(parent, SWT.CHECK);
        btnNotify.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
        btnNotify.setText("Notify me about file changes");
        btnNotify.setSelection(Cfg.db().getBoolean(Key.NOTIFY));
        btnNotify.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                helper.setCfg(Key.NOTIFY, btnNotify.getSelection());
            }
        });
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }

    private class AdvancedDialog extends AeroFSDialog
    {
        public AdvancedDialog(Shell parent)
        {
            super(parent, "Advanced", false, false);
        }

        @Override
        protected void open(Shell shell)
        {
            PreferencesHelper helper = new PreferencesHelper(shell);
            setLayoutForAdvanced(shell);

            helper.createSyncHistory(shell);
            helper.createAPIAccess(shell);
            createSeparator(shell, false);
            helper.createSelectiveSyncButton(shell);
            helper.createLimitBandwidthButton(shell);
            helper.createCanaryControls(shell);
            helper.createUnlinkButton(shell);
        }
    }
}
