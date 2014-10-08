/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.misc;

import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.SanityPoller.ShouldProceed;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class CompExternalRootMissing extends Composite
{
    /**
     * This class handles an external folder that has gone missing.
     *
     * The options presented are "Leave this folder" or "Try Again" for singleuser clients.
     * For multiuser clients, only "Try Again" is valid.
     */
    public CompExternalRootMissing(Composite parent, final String oldAbsPath, ShouldProceed uc)
    {
        super(parent, SWT.NONE);
        String alternateActionText =
                L.isMultiuser() ? "AeroFS cannot proceed until the missing folder is restored."
                : "If the folder cannot be restored on disk, it will be temporarily "
                    + "unlinked on this device. You can restore unlinked folders "
                    + "in the Selective Sync menu.";

        Composite composite = commonLayout(
                "The following " + L.product() + " folder has been moved or deleted:\n"
                + oldAbsPath + "\n\n"
                + alternateActionText);

        Button tryAgainButton = GUIUtil.createButton(composite, SWT.NONE);
        tryAgainButton.setText("Try Again");
        tryAgainButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                // simply dispose of this dialog. If the folder is still missing,
                // the sanity poller will notice it automatically and redisplay
                closeAndDisposeDialog();
            }
        });

        Button defaultBtn = tryAgainButton;
        if (!L.isMultiuser()) {
            Button leaveFolderBtn = GUIUtil.createButton(composite, SWT.NONE);
            leaveFolderBtn.setText("Leave This Folder");
            leaveFolderBtn.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    uc.proceedIf(true);
                    closeAndDisposeDialog();
                }
            });
            defaultBtn = leaveFolderBtn;
        }
        getShell().setDefaultButton(defaultBtn);
    }

    private Composite commonLayout(String errorText)
    {
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = GUIParam.MAJOR_SPACING;
        this.setLayout(gridLayout);

        CLabel errorIcon = new CLabel(this, SWT.NONE);
        errorIcon.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false, 1, 1));
        errorIcon.setImage(getShell().getDisplay().getSystemImage(SWT.ICON_ERROR));

        Label errorLabel = new Label(this, SWT.WRAP);
        GridData gdErrorLabel = new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1);
        gdErrorLabel.widthHint = 360;
        errorLabel.setLayoutData(gdErrorLabel);
        errorLabel.setText(errorText);

        Composite composite = new Composite(this, SWT.NONE);
        FillLayout layout = new FillLayout(SWT.HORIZONTAL);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false, 2, 1));

        return composite;
    }

    /**
     * Close and dispose the dialog.
     */
    private void closeAndDisposeDialog()
    {
        getShell().close();
    }
}
