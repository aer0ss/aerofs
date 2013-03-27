/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.misc;

import com.aerofs.base.id.SID;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;

import javax.annotation.Nullable;

public class DlgRootAnchorUpdater extends AeroFSDialog
{
    private final String _oldAbsPath;
    private final @Nullable SID _sid;

    public DlgRootAnchorUpdater(Shell parent, String oldAbsPath, @Nullable SID sid)
    {
        super(parent, S.RAW_LOCATION_CHANGE, false, false);
        _oldAbsPath = oldAbsPath;
        _sid = sid;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) {
            shell = new Shell(getParent(), getStyle());
        }

        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        new CompRootAnchorUpdater(shell, _oldAbsPath, _sid);

        shell.pack();
    }
}
