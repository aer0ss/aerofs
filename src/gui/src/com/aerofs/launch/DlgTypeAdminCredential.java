/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.launch;

import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import javax.annotation.Nullable;

/**
 * Show this dialog after the user has changed the password on the web site causing the local
 * device unable to login with the stored password.
 */
public class DlgTypeAdminCredential extends AeroFSJFaceDialog
{
    private String _defaultEmail;
    private Text _txtEmail;
    private Text _txtPasswd;
    private boolean _cancelled = true;

    private @Nullable UserID _userID;
    private @Nullable char[] _passwd;

    public DlgTypeAdminCredential(Shell parentShell, String defaultEmail)
    {
        super("Enter Admin Credentials", parentShell, false, false, true, true);

        _defaultEmail = defaultEmail;
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        GUIUtil.setShellIcon(getShell());

        Composite container = (Composite) super.createDialogArea(parent);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = gridLayout.marginTop = GUIParam.MARGIN;
        gridLayout.marginHeight = 0;
        gridLayout.marginBottom = 0;
        gridLayout.verticalSpacing = GUIParam.MAJOR_SPACING;
        gridLayout.numColumns = 2;
        container.setLayout(gridLayout);

        // row 1

        Label label = new Label(container, SWT.WRAP);
        GridData gd = new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1);
        gd.widthHint = 300;
        label.setLayoutData(gd);
        label.setText(S.TYPE_ADMIN_PASSWORD_TO_RECERTIFY_TEAM_SERVER);

        // row 2

        new Label(container, SWT.NONE).setText(S.ADMIN_EMAIL + ":");

        _txtEmail = new Text(container, SWT.BORDER);
        _txtEmail.setText(_defaultEmail);
        _txtEmail.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent ev)
            {
                updateButtons();
            }
        });
        _txtEmail.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // row 3

        new Label(container, SWT.NONE).setText(S.ADMIN_PASSWD + ":");

        _txtPasswd = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPasswd.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent ev)
            {
                updateButtons();
            }
        });
        _txtPasswd.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return container;
    }

    private void updateButtons()
    {
        getButton(IDialogConstants.OK_ID).setEnabled(
                !_txtEmail.getText().isEmpty() && !_txtPasswd.getText().isEmpty());
    }

    /**
     * Create contents of the button bar
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        final Button button = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
                true);
        button.setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            try {
                _userID = UserID.fromExternal(_txtEmail.getText());
            } catch (ExEmptyEmailAddress ex) {
                // The OK button should not be enabled with an empty email.
                assert false;
            }

            _passwd = _txtPasswd.getTextChars();
            _cancelled = false;
        }

        super.buttonPressed(IDialogConstants.OK_ID);
    }

    boolean isCancelled()
    {
        return _cancelled;
    }

    @Nullable UserID getUserID()
    {
        return _userID;
    }

    @Nullable char[] getPasswd()
    {
        return _passwd;
    }
}
