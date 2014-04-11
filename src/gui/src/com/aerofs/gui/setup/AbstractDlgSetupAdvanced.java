/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.setup;

import java.io.File;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;

import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridData;

public abstract class AbstractDlgSetupAdvanced extends AeroFSJFaceDialog {

    abstract protected void createStorageArea(Composite parent);
    abstract protected String getAbsRootAnchorFromTxtField();

    private Text _txtDeviceName;
    private String _deviceName;
    private String _absRootAnchor;

    public AbstractDlgSetupAdvanced(Shell parentShell, String deviceName, String absRootAnchor)
    {
        super("Advanced Options", parentShell, true, false, false, false);
        _deviceName = deviceName;
        _absRootAnchor = absRootAnchor;
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        GUIUtil.setShellIcon(getShell());

        Composite container = initializeDialogArea(parent);

        createDeviceNameField(container);

        new Label(container, SWT.NONE);

        new Label(container, SWT.NONE);

        createStorageArea(container);

        return container;
    }

    /**
     * Create contents of the button bar
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        _deviceName = _txtDeviceName.getText();
        _absRootAnchor = getAbsRootAnchorFromTxtField();
        super.buttonPressed(buttonId);
    }

    public String getDeviceName()
    {
        return _deviceName;
    }

    protected String getAbsoluteRootAnchor()
    {
        assert new File(_absRootAnchor).isAbsolute();
        return _absRootAnchor;
    }

    private void createDeviceNameField(Composite container)
    {
        Label lblComputerName = new Label(container, SWT.NONE);
        lblComputerName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblComputerName.setText(S.SETUP_DEV_ALIAS + ":");

        _txtDeviceName = new Text(container, SWT.BORDER);
        _txtDeviceName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _txtDeviceName.setText(_deviceName);
    }

    private Composite initializeDialogArea(Composite parent)
    {
        Composite container = (Composite) super.createDialogArea(parent);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 2;
        gridLayout.marginRight = 20;
        gridLayout.marginTop = 20;
        gridLayout.marginLeft = 20;
        container.setLayout(gridLayout);
        return container;
    }
}
