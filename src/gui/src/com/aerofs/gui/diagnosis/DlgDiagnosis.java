package com.aerofs.gui.diagnosis;

import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
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
    private Label _lblUnsyncableStatusText;
    private Label _lblUnsyncableStatusImage;

    public DlgDiagnosis(Shell parent, boolean showSystemFiles)
    {
        super(parent, S.WHY_ARENT_MY_FILES_SYNCED, false, true);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());

        GridLayout glShell = new GridLayout(4, false);
        glShell.marginRight = GUIParam.MARGIN;
        glShell.verticalSpacing = GUIParam.VERTICAL_SPACING;
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        shell.setLayout(glShell);

        Label lblUnsyncableFiles = new Label(shell, SWT.NONE);
        lblUnsyncableFiles.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        lblUnsyncableFiles.setFont(GUIUtil.makeBold(lblUnsyncableFiles.getFont()));
        lblUnsyncableFiles.setText("Unsyncable Files");

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

        shell.addControlListener(new ControlAdapter()
        {
            @Override
            public void controlResized(ControlEvent controlEvent)
            {
                getShell().layout();
            }
        });

        compUnsyncable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

        _lblUnsyncableStatusImage = new Label(shell, SWT.NONE);
        _lblUnsyncableStatusImage
                .setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        _lblUnsyncableStatusImage.setImage(Images.get(Images.ICON_NIL));

        _lblUnsyncableStatusText = new Label(shell, SWT.NONE);
        GridData gd_lbl = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_lbl.widthHint = compUnsyncable.getStatusWidthHint(
                _lblUnsyncableStatusText.getFont());
        _lblUnsyncableStatusText.setLayoutData(gd_lbl);

        Button btnClose = GUIUtil.createButton(shell, SWT.NONE);
        btnClose.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });
        btnClose.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        btnClose.setText("Close");

        shell.setMinimumSize(shell.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        compUnsyncable.search();
    }
}
