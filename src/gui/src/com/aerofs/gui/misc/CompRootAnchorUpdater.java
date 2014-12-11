/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.misc;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.error.ErrorMessages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.slf4j.Logger;

import java.io.File;

import static com.aerofs.gui.GUIUtil.createLabel;

public class CompRootAnchorUpdater extends Composite
{
    private final static Logger l = Loggers.getLogger(CompRootAnchorUpdater.class);

    private final String NEW_LOCATION_TEXT = "Select New Location...";
    // N.B. UNLINK_TEXT is expected to end with a period, see errorLabel's text
    private final String UNLINK_TEXT = S.UNLINK_THIS_COMPUTER;
    private final String QUIT_BUTTON_TEXT = "Quit";

    public CompRootAnchorUpdater(Composite parent, final String oldAbsPath)
    {
        super(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = GUIParam.MAJOR_SPACING;
        this.setLayout(gridLayout);

        CLabel errorIcon = new CLabel(this, SWT.NONE);
        errorIcon.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false, 1, 1));
        errorIcon.setImage(getShell().getDisplay().getSystemImage(SWT.ICON_ERROR));

        Label errorLabel = createLabel(this, SWT.WRAP);
        GridData gdErrorLabel = new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1);
        gdErrorLabel.widthHint = 360;
        errorLabel.setLayoutData(gdErrorLabel);
        // TODO: fix this fugly dialog by splitting this label into a nicer multi-component layout
        // This string must be consistent with the string in CLIRootAnchorUpdater
        // TODO (WW) define the string in S.java?
        errorLabel.setText(
                "Your " + S.ROOT_ANCHOR + " was not found in the original location:\n" +
                oldAbsPath + "\n\n" +
                "If you moved the folder, click \"" + NEW_LOCATION_TEXT + "\" " +
                "below, and specify the new location.\n\n" +
                "If you deleted the " + L.product() + " folder, or want to start over, click \"" +
                UNLINK_TEXT + "\" You will be asked to setup " +
                L.product() + " the next time " + L.product() + " launches.\n\n" +
                "If you want to move the missing folder back to its original location, " +
                "click \"" + QUIT_BUTTON_TEXT + ",\" move the folder back to its original " +
                "location, and launch " + L.product() + " again.");

        Composite composite = new Composite(this, SWT.NONE);
        FillLayout layout = new FillLayout(SWT.HORIZONTAL);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false, 2, 1));

        Button newLocationBtn = GUIUtil.createButton(composite, SWT.NONE);
        newLocationBtn.setText(NEW_LOCATION_TEXT);
        newLocationBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (selectRootAnchorLocation(oldAbsPath)) closeAndDisposeDialog();
            }
        });

        Button unlinkBtn = GUIUtil.createButton(composite, SWT.NONE);
        unlinkBtn.setText(UNLINK_TEXT);
        unlinkBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                unlinkRootAnchorAndExit();
            }
        });

        Button quitBtn = GUIUtil.createButton(composite, SWT.NONE);
        quitBtn.setText(QUIT_BUTTON_TEXT);
        quitBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeAndDisposeDialog();
                UI.get().shutdown();
            }
        });

        getShell().setDefaultButton(newLocationBtn);
    }

    /**
     * @return True if a new root anchor path was selected and applied to the CfgDatabase,
     * False otherwise.
     */
    private boolean selectRootAnchorLocation(String oldAbsPath)
    {
        // Select the new Root Anchor.
        DirectoryDialog dd = new DirectoryDialog(getShell(), SWT.SHEET);
        dd.setMessage("Select folder location");
        String rootPath = dd.open();

        if (rootPath == null) return false;

        String newRootPath = RootAnchorUtil.adjustRootAnchor(rootPath, null);
        try {
            RootAnchorUtil.checkNewRootAnchor(oldAbsPath, newRootPath);
            File f = new File(newRootPath);
            if (!f.exists() || !f.isDirectory())
                throw new ExBadArgs("New location:\n" + newRootPath +
                        " does not exist or not a directory");
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.WARN, ErrorMessages.e2msgDeprecated(e) +
                    ". Please select a different folder.");
            return false;
        }

        // Apply the changes to the CfgDatabase.
        try {
            RootAnchorUtil.updateAbsRootCfg(null, newRootPath);
            Cfg.init_(Cfg.absRTRoot(), false);
            GUI.get().show(getShell(), MessageType.INFO, "The location was successfully updated!");
            return true;
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.ERROR,
                    "An error occured while updating the location: " + ErrorMessages.e2msgDeprecated(
                            e));
            l.warn(Util.e(e));
            return false;
        }
    }

    /**
     * Unlink this computer and then shuts down AeroFS
     */
    private void unlinkRootAnchorAndExit()
    {
        if (GUI.get().ask(getShell(), MessageType.WARN, S.UNLINK_THIS_COMPUTER_CONFIRM)) {
            try {
                UIUtil.scheduleUnlinkAndExit();
            } catch (Exception e) {
                GUI.get().show(MessageType.ERROR, "Couldn't unlink the computer " + ErrorMessages.e2msgDeprecated(
                        e));
            }
        }
    }

    /**
     * Close and dispose the dialog.
     */
    private void closeAndDisposeDialog()
    {
        getShell().close();
    }
}
