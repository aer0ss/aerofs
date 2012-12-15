/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.preferences;

import com.aerofs.gui.GUI;
import com.aerofs.lib.L;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;

public class PreferencesUtil
{
    private final Composite _preferences;

    private String _rootAnchor;

    public PreferencesUtil(Composite preferences)
    {
       _preferences = preferences;
    }

    /**
     * @param rootParent
     * @return whether we were successful
     */
    public boolean moveRootAnchor(String rootParent)
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

        if (!GUI.get().ask(_preferences.getShell(), MessageType.QUESTION, "Are you sure you want to move the "
                + L.PRODUCT + " folder and its content from:\n\n" + pathOld + " \n\n to: \n\n " +
                pathNew + "?")) {
            return false;
        }

        DlgMoveRootAnchor dlg = new DlgMoveRootAnchor(_preferences.getShell(), true, pathNew);

        Boolean success = (Boolean) dlg.openDialog();

        if (success != null && success) {
            _rootAnchor = pathNew;
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return Path of new root anchor
     */
    public String getRootAnchorPathFromDirectoryDialog(String prompt)
    {
        DirectoryDialog dd = new DirectoryDialog(_preferences.getShell(), SWT.SHEET);
        dd.setMessage(prompt);
        return dd.open();
    }

    public String getRootAnchor()
    {
        return _rootAnchor;
    }
}
