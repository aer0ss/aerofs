/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.preferences;

import com.aerofs.gui.GUI;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Text;

public class PreferencesHelper
{
    private final Composite _preferences;

    public PreferencesHelper(Composite preferences)
    {
       _preferences = preferences;
    }

    public void selectAndMoveRootAnchor(final Text txtRootAnchor)
    {
        // Have to re-open the directory dialog in a separate stack, since doing it in the same
        // stack would cause strange SWT crashes on OSX :/
        GUI.get().safeAsyncExec(_preferences, new Runnable() {
            @Override
            public void run()
            {
                String root = getRootAnchorPathFromDirectoryDialog();
                if (root == null) return; //User hit cancel
                if (moveRootAnchor(root)) {
                    txtRootAnchor.setText(Cfg.absRootAnchor());
                } else {
                    selectAndMoveRootAnchor(txtRootAnchor);
                }
            }
        });
    }

    /**
     * @return whether we were successful
     */
    private boolean moveRootAnchor(String rootParent)
    {
        String pathOld = Cfg.absRootAnchor();
        String pathNew = RootAnchorUtil.adjustRootAnchor(rootParent);

        try {
            RootAnchorUtil.checkNewRootAnchor(pathOld, pathNew);
        } catch (Exception e) {
            GUI.get().show(_preferences.getShell(), MessageType.WARN, e.getMessage() +
                    ". Please select a different folder.");
            return true;
        }

        if (!GUI.get().ask(_preferences.getShell(), MessageType.QUESTION,
                "Are you sure you want to move the "
                + S.ROOT_ANCHOR + " and its content from:\n\n" + pathOld + "\n\nto:\n\n" +
                pathNew + "?")) {
            return false;
        }

        DlgMoveRootAnchor dlg = new DlgMoveRootAnchor(_preferences.getShell(), true, pathNew);

        Boolean success = (Boolean) dlg.openDialog();

        return success != null && success;
    }

    /**
     * @return Path of new root anchor
     */
    private String getRootAnchorPathFromDirectoryDialog()
    {
        DirectoryDialog dd = new DirectoryDialog(_preferences.getShell(), SWT.SHEET);
        dd.setMessage("Select " + S.ROOT_ANCHOR);
        return dd.open();
    }
}
