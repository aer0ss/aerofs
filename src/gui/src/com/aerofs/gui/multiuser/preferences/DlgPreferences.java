/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.multiuser.preferences;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;

public class DlgPreferences extends AeroFSDialog
{
    public DlgPreferences(Shell parent)
    {
        super(parent, L.PRODUCT + " " + S.PREFERENCES, false, false);
    }
    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro())
            shell = new Shell(getParent(), getStyle());
        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        new CompPreferences(shell);

        shell.pack();
    }
}
