package com.aerofs.gui.unsyncablefiles;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIParam;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;

public class DlgUnsyncableFiles extends AeroFSDialog
{
    public DlgUnsyncableFiles(Shell parent)
    {
        super(parent, "Unsyncable Files", false, true);
    }

    @Override
    protected void open(Shell shell)
    {
        new CompUnsyncableFiles(shell);

        FillLayout layout = new FillLayout();
        layout.marginWidth = GUIParam.MARGIN;
        layout.marginHeight = GUIParam.MARGIN;
        shell.setLayout(layout);
    }
}
