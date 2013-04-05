/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.preferences;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.gui.GUI;
import com.aerofs.gui.preferences.PreferencesHelper;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

public class MultiuserCompPreferences extends Composite
{
    private final Button _btnAutoExportEnabled;
    public MultiuserCompPreferences(Composite parent)
    {
        super(parent, SWT.NONE);

        PreferencesHelper helper = new PreferencesHelper(this);
        helper.setLayout();

        // Device name row

        helper.createDeviceNameLabelAndText();

        // Manage devices row

        helper.createManageDevices("Manage all Team Servers", WWW.TEAM_SERVER_DEVICES_URL);

        // Root anchor relocation row

        helper.createRelocationLabelAndText();

        // Autoexport (show files on filesystem) row
        _btnAutoExportEnabled = new Button(this, SWT.CHECK);
        _btnAutoExportEnabled.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        _btnAutoExportEnabled.setText(S.ENABLE_FILESYSTEM_VIEW);
        _btnAutoExportEnabled.setSelection(Cfg.db().getNullable(Key.AUTO_EXPORT_FOLDER) != null);
        _btnAutoExportEnabled.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setExportFolder(_btnAutoExportEnabled.getSelection());
            }
        });

        // Spinner row

        helper.createSpinner();

        helper.registerShellListeners();
    }

    private void setExportFolder(Boolean enabled)
    {
        try {
            if (enabled != null) {
                if (enabled) {
                    // Enabling export, warn user
                    String exportPath = Cfg.db().get(Key.ROOT);
                    GUI.get().show(getShell(), MessageType.INFO, "You are enabling filesystem " +
                            "view of your files. You will be able to view files stored under " +
                            exportPath + "\n\nIt may take a while for all files to appear, and " +
                            L.PRODUCT + " may seem unresponsive during that time.");
                    Cfg.db().set(Key.AUTO_EXPORT_FOLDER, exportPath);
                } else {
                    Cfg.db().set(Key.AUTO_EXPORT_FOLDER, null);
                }
            }
            RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
            try {
                ritual.reloadConfig();
            } finally {
                ritual.close();
            }
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.ERROR, "Couldn't change filesystem view option "
                    + UIUtil.e2msg(e));
        }
    }
}
