/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.preferences;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.gui.GUI;
import com.aerofs.gui.preferences.PreferencesHelper;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
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

        PreferencesHelper helper = new PreferencesHelper(this);
        helper.setLayout();

        // Device name row

        helper.createDeviceNameLabelAndText();

        // Manage devices row

        helper.createManageDevices("Manage all Team Servers", WWW.TEAM_SERVER_DEVICES_URL.get());

        // Root anchor relocation row

        helper.createRelocationLabelAndText();

        // Spinner row

        helper.createSpinner();

        helper.registerShellListeners();
    }
}
