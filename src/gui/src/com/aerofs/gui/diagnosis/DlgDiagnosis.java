package com.aerofs.gui.diagnosis;

import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.diagnosis.CompUnsyncableFiles.IStatus;
import com.aerofs.lib.S;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Label;

public class DlgDiagnosis extends AeroFSDialog
{
    private final static int COMP_HEIGHT = 120;

    private final boolean _showSystemfiles;
    private Button _btnClose;
    private Label _lblUnsyncableStatusText;
    private Label _lblUnsyncableStatusImage;

    public DlgDiagnosis(Shell parent, boolean showSystemFiles)
    {
        super(parent, S.WHY_ARENT_MY_FILES_SYNCED, false, true);
        _showSystemfiles = showSystemFiles;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());
            shell.setSize(770, 396);

        GridLayout glShell = new GridLayout(4, false);
        glShell.marginRight = GUIParam.MARGIN;
        glShell.verticalSpacing = 8;
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginTop = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        shell.setLayout(glShell);

        new Label(shell, SWT.NONE);
        Label lblNewLabel_2 = new Label(shell, SWT.WRAP);
        GridData gd_lblNewLabel_2 = new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1);
        gd_lblNewLabel_2.widthHint = 357;
        lblNewLabel_2.setLayoutData(gd_lblNewLabel_2);
        lblNewLabel_2.setText("Files may not be synced if they are in conflict or not supported by " +
                S.PRODUCT + ". Please review these files below.");

        Label lblConflictFiles = new Label(shell, SWT.NONE);
        lblConflictFiles.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
        lblConflictFiles.setFont(GUIUtil.makeBold(lblConflictFiles.getFont()));
        lblConflictFiles.setText("Conflict Files");

        Label lblNewLabel = new Label(shell, SWT.NONE);
        GridData gd_lblNewLabel = new GridData(SWT.LEFT, SWT.FILL, false, true, 1, 1);
        gd_lblNewLabel.widthHint = GUIParam.MARGIN;
        lblNewLabel.setLayoutData(gd_lblNewLabel);

        CompConflictFiles compConflict = new CompConflictFiles(shell, SWT.NONE, _showSystemfiles);
        GridData gd__compConflict = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
        gd__compConflict.heightHint = COMP_HEIGHT;
        compConflict.setLayoutData(gd__compConflict);
        compConflict.search();

        Label lblUnsyncableFiles = new Label(shell, SWT.NONE);
        lblUnsyncableFiles.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
        lblUnsyncableFiles.setFont(GUIUtil.makeBold(lblUnsyncableFiles.getFont()));
        lblUnsyncableFiles.setText("Unsyncable Files");
        new Label(shell, SWT.NONE);

        CompUnsyncableFiles compUnsyncable = new CompUnsyncableFiles(shell, SWT.NONE,
            new IStatus() {
                @Override
                public void setStatusText(String text)
                {
                    _lblUnsyncableStatusText.setText(text);
                }

                @Override
                public void setStatusImage(Image img)
                {
                    _lblUnsyncableStatusImage.setImage(img);
                }
            });

        GridData gd__compUnsyncable = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
        gd__compUnsyncable.heightHint = COMP_HEIGHT;
        compUnsyncable.setLayoutData(gd__compUnsyncable);
        new Label(shell, SWT.NONE);

        _lblUnsyncableStatusImage = new Label(shell, SWT.NONE);
        _lblUnsyncableStatusImage.setImage(Images.get(Images.ICON_NIL));

        _lblUnsyncableStatusText = new Label(shell, SWT.NONE);
        GridData gd_lbl = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_lbl.widthHint = compUnsyncable.getStatusWidthHint(
                _lblUnsyncableStatusText.getFont());
        _lblUnsyncableStatusText.setLayoutData(gd_lbl);

        _btnClose = new Button(shell, SWT.NONE);
        _btnClose.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });
        _btnClose.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _btnClose.setText("Close");

        compUnsyncable.search();
    }
}
