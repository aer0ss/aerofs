package com.aerofs.gui.sharing.manage;

import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.jface.dialogs.IDialogConstants;

import com.aerofs.gui.GUIParam;
import com.aerofs.gui.sharing.manage.CompUserList.ILoadListener;
import com.aerofs.lib.Path;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

public class CompManageUsers extends Composite
{
    private final Button _btnClose;

    private final Path _path;
    private final Button _btnCancel;
    private final Composite _composite;
    private final CompUserList _compUserList;

    public CompManageUsers(Composite parent, Path path, ILoadListener ll)
    {
        super(parent, SWT.NONE);
        _path = path;

        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        setLayout(glShell);

        _compUserList = new CompUserList(this, _path, ll);

        GridData gd__compAddresses = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd__compAddresses.heightHint = 60;
        _compUserList.setLayoutData(gd__compAddresses);

        _composite = new Composite(this, SWT.NONE);
        GridLayout glComposite = new GridLayout(2, false);
        glComposite.marginWidth = 0;
        glComposite.marginHeight = 0;
        glComposite.marginTop = GUIParam.MAJOR_SPACING - glShell.verticalSpacing;
        _composite.setLayout(glComposite);
        _composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        _btnCancel = new Button(_composite, SWT.NONE);
        _btnCancel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        _btnCancel.setText("Unshare Folder");
        _btnCancel.setVisible(false);

        _btnClose = new Button(_composite, SWT.NONE);
        _btnClose.setText(IDialogConstants.CLOSE_LABEL);
        _btnClose.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                getShell().close();
            }
        });
    }

    public int getUsersCount()
    {
        return _compUserList.getUsersCount();
    }
}
