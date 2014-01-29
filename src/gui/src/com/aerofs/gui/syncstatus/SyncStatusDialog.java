/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.syncstatus;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.common.PathInfoProvider;
import com.aerofs.lib.Path;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

public class SyncStatusDialog extends AeroFSDialog
{
    private final Path _path;

    public SyncStatusDialog(Shell parent, Path path)
    {
        super(parent, "Sync Status", false, true);

        _path = path;
    }

    @Override
    protected void open(Shell shell)
    {
        Label lblFileIcon = new Label(shell, SWT.NONE);
        Label lblFilename = new Label(shell, SWT.NONE);
        SyncStatusTable tblSyncStatus = new SyncStatusTable(shell);
        Link  lnkHelp = new Link(shell, SWT.WRAP);

        Composite buttonBar = GUIUtil.newPackedButtonContainer(shell);
        Button btnClose = GUIUtil.createButton(buttonBar, SWT.PUSH);

        PathInfoProvider provider = new PathInfoProvider(shell);
        lblFileIcon.setImage(provider.getFileIcon(_path));
        lblFilename.setText(provider.getFilename(_path));

        lnkHelp.setText("<a>Learn more about Sync Status.</a>");
        lnkHelp.addSelectionListener(GUIUtil.createUrlLaunchListener("https://support.aerofs.com/entries/26242815"));

        btnClose.setText(IDialogConstants.CLOSE_LABEL);
        btnClose.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });

        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = GUIParam.MARGIN;
        layout.marginHeight = GUIParam.MARGIN;
        layout.verticalSpacing = GUIParam.VERTICAL_SPACING;
        shell.setLayout(layout);

        lblFileIcon.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        lblFilename.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
        tblSyncStatus.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
        lnkHelp.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));

        shell.pack();
        shell.setMinimumSize(shell.getSize().x, 320);

        tblSyncStatus.loadFromPath(_path);
    }
}
