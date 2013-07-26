/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.preferences;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.preferences.PreferencesHelper;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgStoragePolicy;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

public class MultiuserCompPreferences extends Composite
{
    public MultiuserCompPreferences(Composite parent)
    {
        super(parent, SWT.NONE);

        _cfgStoragePolicy = new CfgStoragePolicy(Cfg.db());

        PreferencesHelper helper = new PreferencesHelper(this);
        helper.setLayout();

        // Device name row

        helper.createDeviceNameLabelAndText();

        // Manage devices row

        helper.createManageDevices("Manage all Team Servers", WWW.TEAM_SERVER_DEVICES_URL.get());

        // Root anchor relocation row

        helper.createRelocationLabelAndText();

        // Enable sync-history button

        final Button btnHistory = GUIUtil.createButton(this, SWT.CHECK);
        btnHistory.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        btnHistory.setText(S.ENABLE_SYNC_HISTORY);
        btnHistory.setSelection(_cfgStoragePolicy.useHistory());
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

            private void setCfg(Key dbKey, boolean newValue)
            {
                try {
                    Cfg.db().set(dbKey, newValue);
                } catch (Exception e) {
                    GUI.get().show(getShell(), MessageType.ERROR,
                            "Couldn't update configuration value for "
                            + Key.SYNC_HISTORY.toString() + ErrorMessages.e2msgDeprecated(e));
                }
            }
        });

        // Spinner row

        helper.createSpinner();

        helper.registerShellListeners();
    }

    private CfgStoragePolicy _cfgStoragePolicy;
}
