package com.aerofs.gui.sharing.folders;

import com.aerofs.gui.GUIParam;
import com.aerofs.gui.sharing.folders.CompFoldersTree.IListener;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;

import javax.annotation.Nullable;

public class DlgFolders extends AeroFSDialog
{
    private Button _btnOk;
    private @Nullable Path _sel;

    public DlgFolders(Shell parent, boolean sheetStyle)
    {
        super(parent, "Select Folder", sheetStyle, true);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());
        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        createLayout(new Composite(shell, SWT.NONE));
    }

    private void createLayout(Composite container)
    {
        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        container.setLayout(glShell);

        Label lblLabel = new Label(container, SWT.NONE);
        GridData gdLabel = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1);
        gdLabel.heightHint = 22;
        lblLabel.setLayoutData(gdLabel);
        lblLabel.setText("Select an " + L.product() + " folder to share:");

        CompFoldersTree compList = new CompFoldersTree(container, new IListener()
        {
            @Override
            public void selected(Path path)
            {
                _sel = path;
                _btnOk.setEnabled(_sel != null);
            }

            @Override
            public void defaultSelected(Path path)
            {
                selected(path);
                if (_sel != null) closeDialog(_sel);
            }
        });

        GridData gdCompList = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gdCompList.widthHint = 390;
        gdCompList.heightHint = 250;
        compList.setLayoutData(gdCompList);

        Composite composite = new Composite(container, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        FillLayout fl = new FillLayout(SWT.HORIZONTAL);
        fl.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        composite.setLayout(fl);

        _btnOk = GUIUtil.createButton(composite, SWT.NONE);
        _btnOk.setText(IDialogConstants.OK_LABEL);
        _btnOk.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                closeDialog(_sel);
            }
        });
        _btnOk.setEnabled(false);

        Button btnCancel = GUIUtil.createButton(composite, SWT.NONE);
        btnCancel.setText(IDialogConstants.CANCEL_LABEL);
        btnCancel.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                getShell().close();
            }
        });
    }
}
