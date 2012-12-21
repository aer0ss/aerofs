/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.preferences;

import com.aerofs.gui.preferences.PreferencesHelper;
import org.eclipse.swt.SWT;
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

        // Root anchor relocation row

        helper.createRelocationLabelAndText();

        // Spinner row

        helper.createSpinner();

        helper.registerShellListeners();
    }
}
