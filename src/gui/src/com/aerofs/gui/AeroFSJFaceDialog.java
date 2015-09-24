package com.aerofs.gui;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import javax.annotation.Nullable;

import com.aerofs.lib.S;

/**
 * deprecated, this class is going away, use AeroFSDialog instead
 *
 * override the method below to remove the bottom bar:
 *      protected Control createButtonBar(Composite parent)
 */
public class AeroFSJFaceDialog extends Dialog {

    private final String _title;

    /**
     * @param title the title of the dialog. will be suffixed with S.TITLE_SUFFIX
     * null to use S.DEFAULT_TITLE
     */
    public AeroFSJFaceDialog(@Nullable String title, Shell parentShell, boolean sheet,
            boolean resizable, boolean close)
    {
        super(parentShell);

        setBlockOnOpen(false);

        _title = title;

        setShellStyle(GUIUtil.createShellStyle(sheet, resizable, close));
    }

    @Override
    protected void configureShell(Shell newShell)
    {
        GUI.get().registerShell(newShell, getClass());

        super.configureShell(newShell);
        GUIUtil.setShellIcon(newShell);
        newShell.setText(_title == null ? S.DEFAULT_DIALOG_TITLE : _title + S.DIALOG_TITLE_SUFFIX);
    }

    @Override
    protected Point getInitialSize()
    {
        return getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT);
    }

    @Override
    public int open()
    {
        super.open();

        Shell sh = getShell();
        sh.forceActive();

        while (!sh.isDisposed()) {
            if (!sh.getDisplay().readAndDispatch()) {
                sh.getDisplay().sleep();
            }
        }

        return getReturnCode();
    }
}
