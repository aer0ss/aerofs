/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.manage;

import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
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
import org.eclipse.swt.widgets.Link;

import static com.aerofs.gui.GUIUtil.createUrlLaunchListener;

public class CompManageUsers extends Composite
{
    private final Button _btnClose;

    private final Path _path;
    private final Composite _composite;
    private final CompUserList _compUserList;
    private final CompSpin _compSpin;

    public CompManageUsers(Composite parent, Path path, ILoadListener ll)
    {
        super(parent, SWT.NONE);
        _path = path;

        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        setLayout(glShell);

        _compUserList = new CompUserList(this);
        _compUserList.setLoadListener(ll);
        _compUserList.load(_path);

        GridData gd__compAddresses = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd__compAddresses.heightHint = 60;
        _compUserList.setLayoutData(gd__compAddresses);

        _composite = new Composite(this, SWT.NONE);
        GridLayout glComposite = new GridLayout(3, false);
        glComposite.marginWidth = 0;
        glComposite.marginHeight = 0;
        glComposite.marginTop = GUIParam.MAJOR_SPACING - glShell.verticalSpacing;
        _composite.setLayout(glComposite);
        _composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        Link link = new Link(_composite, SWT.NONE);
        link.setText("<a>What are Owner, Editor, and Viewer?</a>");
        link.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        link.addSelectionListener(createUrlLaunchListener(S.URL_ROLES));

        _compSpin = new CompSpin(_composite, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _compUserList.setSpinner(_compSpin);

        _btnClose = GUIUtil.createButton(_composite, SWT.NONE);
        _btnClose.setText(IDialogConstants.CLOSE_LABEL);
        _btnClose.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _btnClose.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                getShell().close();
            }
        });
    }
}
