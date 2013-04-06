package com.aerofs.gui.sharing.folders;

import javax.annotation.Nullable;

import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.jface.dialogs.IDialogConstants;

import com.aerofs.gui.GUIParam;
import com.aerofs.gui.sharing.folders.CompFoldersTree.IListener;
import com.aerofs.lib.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Label;

public class CompFolders extends Composite
{
    private final Button _btnCancel;
    private final Button _btnOk;
    private final CompFoldersTree _compList;
    private final Composite composite;
    private @Nullable Path _sel;

    public CompFolders(Composite parent)
    {
        super(parent, SWT.NONE);

        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        setLayout(glShell);

        Label lblNewLabel = new Label(this, SWT.NONE);
        GridData gd_lblNewLabel = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1);
        gd_lblNewLabel.heightHint = 26;
        lblNewLabel.setLayoutData(gd_lblNewLabel);
        lblNewLabel.setText("Select an " + L.product() + " folder to share:");

        _compList = new CompFoldersTree(this, new IListener() {
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
                if (_sel != null) work();
            }
        });

        GridData gd__compList = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd__compList.widthHint = 390;
        gd__compList.heightHint = 194;
        _compList.setLayoutData(gd__compList);

        composite = new Composite(this, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        FillLayout fl = new FillLayout(SWT.HORIZONTAL);
        fl.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        composite.setLayout(fl);

        _btnOk = new Button(composite, SWT.NONE);
        _btnOk.setText(IDialogConstants.OK_LABEL);
        _btnOk.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                work();
            }
        });
        _btnOk.setEnabled(false);

        _btnCancel = new Button(composite, SWT.NONE);
        _btnCancel.setText(IDialogConstants.CANCEL_LABEL);
        _btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                getShell().close();
            }
        });
    }

    private void work()
    {
        assert _sel != null;
        getShell().close();

        GUIUtil.createOrManageSharedFolder(_sel);
    }
}
