/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser;

import com.aerofs.gui.AeroFSTitleAreaDialog;
import com.aerofs.gui.setup.DlgSetupCommon;
import com.aerofs.gui.setup.DlgSetupCommon.IDlgSetupCommonCallbacks;
import com.aerofs.gui.setup.IDlgSetup;
import com.aerofs.ui.UI;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import javax.annotation.Nullable;

import static com.aerofs.gui.setup.DlgSetupCommon.shouldAlwaysOnTop;
import static org.eclipse.jface.dialogs.IDialogConstants.OK_ID;

public class MultiuserDlgSetup extends AeroFSTitleAreaDialog
        implements IDlgSetup, IDlgSetupCommonCallbacks
{
    private final DlgSetupCommon _common;

    public MultiuserDlgSetup(Shell parentShell)
            throws Exception
    {
        super(null, parentShell, false, shouldAlwaysOnTop(), false);
        _common = new DlgSetupCommon(this);
    }

    /**
     * Create contents of the dialog
     */
    @SuppressWarnings("all")
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Control area = super.createDialogArea(parent);
        Composite container = _common.createContainer(area, 2);

        // row 1

        _common.createUserIDInputLabelAndText(container);

        // row 2

        _common.createPasswordLabelAndText(container);

        // row 3

        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);

        // row 4

        _common.createBottomComposite(container);

        _common.setBottomCompositeTopControlForEnabledState();

        // done with rows

        setTitle(_common.getTitle());

        _common.getUserIDText().setFocus();

        container.setTabList(new Control[] {
                _common.getUserIDText(),
                _common.getPasswordText()
        });

        return area;
    }

    @Override
    public void openDialog()
    {
        open();
    }

    @Override
    public void closeDialog()
    {
        close();
    }

    @Override
    public void verify(@Nullable String email)
    {
        boolean ready = _common.isReady(email);

        getButtonBarButton(OK_ID).setEnabled(ready);

        _common.setOkayStatus();
    }

    @Override
    protected void configureShell(Shell newShell)
    {
        super.configureShell(newShell);

        _common.configureShell(newShell);
    }

    /**
     * Create contents of the button bar
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        _common.createButtonBarButtons(parent);
    }

    @Override
    public void createButtonBarButton(Composite parent, int id, String text, boolean setDefault)
    {
        createButton(parent, id, text, setDefault);
    }

    @Override
    public Button getButtonBarButton(int id)
    {
        return getButton(id);
    }

    @Override
    public void preSetup()
    {
    }

    @Override
    public void runSetup(String userID, char[] passwd)
            throws Exception
    {
        UI.controller().setupTeamServer(userID, new String(passwd),
                _common.getAbsRootAnchor(), _common.getDeviceName(), null);
    }

    @Override
    public boolean isCancelled()
    {
        return _common.isCanelled();
    }

    @Override
    public Composite getBottomCompositeTopControlWhenEnabled()
    {
        return null;
    }

    @Override
    public void setControlState(boolean b)
    {
    }

    @Override
    public boolean isExistingUser()
    {
        return true;
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            _common.work();
        } else {
            super.buttonPressed(buttonId);
        }
    }
}
