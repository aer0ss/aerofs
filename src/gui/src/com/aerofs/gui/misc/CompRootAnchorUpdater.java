/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.misc;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
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

    private final CLabel _errorIcon;
    private final Label _errorLabel;

    private final Button _newLocationBtn;
    private final Button _unlinkBtn;
    private final Button _quitBtn;

    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();
    private final Composite composite;

    public CompRootAnchorUpdater(Composite parent)
    {
        super(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = OSUtil.isOSX() ? 20 : 26;
        gridLayout.verticalSpacing = 3;
        this.setLayout(gridLayout);

        _errorIcon = new CLabel(this, SWT.NONE);
        _errorIcon.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false, 1, 1));
        _errorIcon.setImage(getShell().getDisplay().getSystemImage(SWT.ICON_ERROR));

        _errorLabel = new Label(this, SWT.NONE);
        _errorLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 1, 1));
        _errorLabel.setText("Your " + S.PRODUCT + " folder was not found" +
                " in the original location:\n" +
                Cfg.absRootAnchor() + "\n\n" +
                "If you moved the " + S.PRODUCT + " folder, click \"Select New Location...\"\n" +
                "below, and specify the new location.\n\n" +
                "If you deleted the " + S.PRODUCT + " folder, or want to start over, click\n" +
                "\"Unlink This Computer...\". You will be asked to setup " +
                S.PRODUCT + "\nthe next time you launch.\n\n" +
                "If you want to move the " + S.PRODUCT + " folder back to its original\nlocation "
                + ", click \"Quit AeroFS\", move it back to its location,\nand launch " +
                S.PRODUCT + " again.\n\n"
        );
        composite = new Composite(this, SWT.NONE);
        composite.setLayout(new FillLayout(SWT.HORIZONTAL));
        composite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1));

        _newLocationBtn = new Button(composite, SWT.NONE);
        _newLocationBtn.setText("Select New Location...");
        _newLocationBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (selectRootAnchorLocation()) closeAndDisposeDialog();
            }
        });

        _unlinkBtn = new Button(composite, SWT.NONE);
        _unlinkBtn.setText("Unlink This Computer...");

        _quitBtn = new Button(composite, SWT.NONE);
        _quitBtn.setText("Quit " + S.PRODUCT);
        _quitBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeAndDisposeDialog();
                UI.get().shutdown();
                System.exit(0);
            }
        });
        _unlinkBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                unlinkRootAnchorAndExit();
            }
        });

        getShell().setDefaultButton(_newLocationBtn);
    }


    /**
     * @return True if a new root anchor path was selected and applied to the CfgDatabase,
     * False otherwise.
     */
    private boolean selectRootAnchorLocation()
    {
        // Select the new Root Anchor.
        DirectoryDialog dd = new DirectoryDialog(getShell(), SWT.SHEET);
        dd.setMessage("Select " + S.SETUP_ANCHOR_ROOT);
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
                    S.PRODUCT + "' new location was updated successfully!");
            return true;
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.ERROR,
                    "An error occured while applying the new location for the " + S.PRODUCT +
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
                UIUtil.unlinkAndExit(_factFile);
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
