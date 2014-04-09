/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

public class DlgManageSharedFolders extends AeroFSDialog
{
    public DlgManageSharedFolders(Shell parent)
    {
        super(parent, "Manage Shared Folders", false, true);
    }

    @Override
    protected void open(Shell sh)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            sh = new Shell(getParent(), getStyle());

        final Shell shell = sh;
        GridLayout grid = new GridLayout(1, false);
        grid.marginHeight = GUIParam.MARGIN;
        grid.marginWidth = GUIParam.MARGIN;
        grid.horizontalSpacing = 4;
        grid.verticalSpacing = 4;
        shell.setLayout(grid);

        SashForm sashForm = new SashForm(shell, SWT.HORIZONTAL | SWT.SMOOTH);
        GridData sashData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        sashData.heightHint = 300;
        sashForm.setLayoutData(sashData);
        sashForm.setSashWidth(7);

        SharedFolderList folderList = new SharedFolderList(sashForm, SWT.NONE);
        MemberList memberList = new MemberList(sashForm, SWT.NONE);
        folderList.setMemberList(memberList);

        // set default proportion as the golden split between version tree and version table
        // NOTE: must be done AFTER children have been added to the SashForm
        sashForm.setWeights(new int[] {382, 618});

        folderList.refreshAsync();
    }

    static Composite newTableWrapper(Composite parent)
    {
        Composite c = new Composite(parent, SWT.NONE);
        GridLayout lc = new GridLayout(1, false);
        lc.marginBottom = 0;
        lc.marginTop = 0;
        lc.marginRight = 0;
        lc.marginLeft = 0;
        lc.marginHeight = 0;
        lc.marginWidth = 0;
        lc.horizontalSpacing = 0;
        lc.verticalSpacing = GUIParam.VERTICAL_SPACING;
        c.setLayout(lc);
        return c;
    }
}
