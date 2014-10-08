/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.misc;

import com.aerofs.base.id.SID;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import com.aerofs.ui.SanityPoller.ShouldProceed;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;

import javax.annotation.Nullable;

public class DlgRootAnchorUpdater extends AeroFSDialog
{
    private final String _oldAbsPath;
    private final @Nullable SID _sid;
    private final ShouldProceed _uc;

    public DlgRootAnchorUpdater(Shell parent, String oldAbsPath, @Nullable SID sid, ShouldProceed uc)
    {
        super(parent, S.RAW_LOCATION_CHANGE, false, false);
        _oldAbsPath = oldAbsPath;
        _sid = sid;
        _uc = uc;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) {
            shell = new Shell(getParent(), getStyle());
        }

        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        if (_sid == null) {
            new CompRootAnchorUpdater(shell, _oldAbsPath);
        } else {
            new CompExternalRootMissing(shell, _oldAbsPath, _uc);
        }

        shell.pack();
    }
}
