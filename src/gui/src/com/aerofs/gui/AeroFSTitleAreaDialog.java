package com.aerofs.gui;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.lib.S;

/**
 * This class is deprecated.  New code should prefer to use AeroFSDialog.
 */
public class AeroFSTitleAreaDialog extends TitleAreaDialog {

    private final String _title;

    /**
     * @param title see AeroFSDialog
     * @param alwaysOnTop valid only if !sheet
     * @param close valid only if !sheet
     */
    public AeroFSTitleAreaDialog(String title, Shell parentShell, boolean sheet,
            boolean alwaysOnTop, boolean close)
    {
        super(parentShell);

        _title = title;

        if (sheet) {
            setShellStyle(SWT.SHEET);
        } else {
                setShellStyle(
                        (alwaysOnTop ? GUIUtil.alwaysOnTop() : 0) |
                        (close ? SWT.DIALOG_TRIM : (SWT.DIALOG_TRIM & ~SWT.CLOSE)));
        }
    }

    @Override
    protected void configureShell(Shell newShell)
    {
        super.configureShell(newShell);
        GUIUtil.setShellIcon(newShell);
        newShell.setText(_title == null ? S.DEFAULT_DIALOG_TITLE :
            _title + S.DIALOG_TITLE_SUFFIX);
    }

    @Override
    protected Point getInitialSize()
    {
        return getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT);
        // return new Point(450, 378);
    }

    // TODO: make this abstract, or go away
    public boolean isCancelled()
    {
        return true;
    }
}
