/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.misc;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.labeling.L;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import org.apache.log4j.Logger;
import com.aerofs.lib.Util;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.widgets.Button;
import com.aerofs.lib.S;
import org.eclipse.swt.layout.FillLayout;
import java.io.File;

public class CompRootAnchorUpdater extends Composite
{
    private final static Logger l = Util.l(CompRootAnchorUpdater.class);

    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    public CompRootAnchorUpdater(Composite parent)
    {
        super(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = 3;
        this.setLayout(gridLayout);

        CLabel errorIcon = new CLabel(this, SWT.NONE);
        errorIcon.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false, 1, 1));
        errorIcon.setImage(getShell().getDisplay().getSystemImage(SWT.ICON_ERROR));

        final String NEW_LOCATION_TEXT = "Select New Location...";
        final String QUIT_BUTTON_TEXT = "Quit";

        Label errorLabel = new Label(this, SWT.WRAP);
        GridData gdErrorLabel = new GridData(SWT.FILL, SWT.TOP, true, true, 1, 1);
        gdErrorLabel.widthHint = 360;
        errorLabel.setLayoutData(gdErrorLabel);
        errorLabel.setText(
                "Your " + L.PRODUCT + " folder was not found in the original location:\n" +
                Cfg.absRootAnchor() + "\n\n" +
                "If you moved the folder, click \"" + NEW_LOCATION_TEXT + "\" " +
                "below, and specify the new location.\n\n" +
                "If you deleted the folder, or want to start over, click " +
                "\"" + S.UNLINK_THIS_COMPUTER + "\". You will be asked to setup " +
                L.PRODUCT + " the next time you launch.\n\n" +
                "If you want to move the folder back to its original location, " +
                "click \"" + QUIT_BUTTON_TEXT + "\", move it back to its location, and launch " +
                L.PRODUCT + " again.");

        Composite composite = new Composite(this, SWT.NONE);
        composite.setLayout(new FillLayout(SWT.HORIZONTAL));
        composite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1));

        Button newLocationBtn = new Button(composite, SWT.NONE);
        newLocationBtn.setText(NEW_LOCATION_TEXT);
        newLocationBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (selectRootAnchorLocation()) closeAndDisposeDialog();
            }
        });

        Button unlinkBtn = new Button(composite, SWT.NONE);
        unlinkBtn.setText(S.UNLINK_THIS_COMPUTER);

        Button quitBtn = new Button(composite, SWT.NONE);
        quitBtn.setText(QUIT_BUTTON_TEXT);
        quitBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeAndDisposeDialog();
                UI.get().shutdown();
                System.exit(0);
            }
        });
        unlinkBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                unlinkRootAnchorAndExit();
            }
        });

        getShell().setDefaultButton(newLocationBtn);
    }


    /**
     * @return True if a new root anchor path was selected and applied to the CfgDatabase,
     * False otherwise.
     */
    private boolean selectRootAnchorLocation()
    {
        // Select the new Root Anchor.
        DirectoryDialog dd = new DirectoryDialog(getShell(), SWT.SHEET);
        dd.setMessage("Select folder location");
        String rootPath = dd.open();

        if (rootPath == null) return false;

        String oldRootPath = Cfg.absRootAnchor();
        String newRootPath = RootAnchorUtil.adjustRootAnchor(rootPath);
        try {
            RootAnchorUtil.checkNewRootAnchor(oldRootPath, newRootPath);
            File f = new File(newRootPath);
            if (!f.exists() || !f.isDirectory())
                throw new ExBadArgs("New location:\n" + newRootPath +
                        " does not exist or not a directory");
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.WARN, UIUtil.e2msg(e) +
                    ". Please select a different folder.");
            return false;
        }

        // Apply the changes to the CfgDatabase.
        try {
            Cfg.db().set(Key.ROOT, newRootPath);
            Cfg.init_(Cfg.absRTRoot(), false);
            GUI.get().show(getShell(), MessageType.INFO,
                    L.PRODUCT + "' new location was updated successfully!");
            return true;
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.ERROR,
                    "An error occured while applying the new location for the " + L.PRODUCT +
                            " folder " + UIUtil.e2msg(e));
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
                UIUtil.scheduleUnlinkAndExit(_factFile);
            } catch (Exception e) {
                GUI.get().show(MessageType.ERROR, "Couldn't unlink the computer " + UIUtil.e2msg(e));
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
