/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.misc;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;

public class DlgRootAnchorUpdater extends AeroFSDialog
{
    public DlgRootAnchorUpdater(Shell parent)
    {
        super(parent, S.RAW_LOCATION_CHANGE, false, false);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) {
            shell = new Shell(getParent(), getStyle());
        }

        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        new CompRootAnchorUpdater(shell);

        shell.pack();
    }
}
