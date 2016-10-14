/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.preferences;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.preferences.PreferencesHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import static com.aerofs.gui.preferences.PreferencesHelper.createSeparator;
import static com.aerofs.gui.preferences.PreferencesHelper.setLayoutForAdvanced;

public class MultiuserCompPreferences extends Composite
{
    public MultiuserCompPreferences(Composite parent)
    {
        super(parent, SWT.NONE);

        PreferencesHelper helper = new PreferencesHelper(this);
        PreferencesHelper.setLayout(this);

        helper.createDeviceNameLabelAndText();
        helper.createManageDevices("Manage all Team Servers", WWW.TEAM_SERVER_DEVICES_URL);
        helper.createRelocationLabelAndText();
        helper.createAdvancedButton(this, new AdvancedDialog(getShell()));

        helper.registerShellListeners();
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
            helper.createUnlinkButton(shell);
        }
    }
}
