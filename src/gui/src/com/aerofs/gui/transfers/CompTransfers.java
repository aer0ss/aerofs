package com.aerofs.gui.transfers;

import com.aerofs.gui.TransferState;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.SWT;
import com.aerofs.gui.GUIParam;

import org.eclipse.swt.widgets.Label;

import static com.aerofs.gui.GUIUtil.createLabel;

public class CompTransfers extends Composite
{
    private final CompTransfersTable _view;

    private final CompTransferStat _compStat;

    public CompTransfers(Composite parent)
    {
        super(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.horizontalSpacing = 0;
        gridLayout.verticalSpacing = 0;
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        setLayout(gridLayout);

        Composite composite_1 = new Composite(this, SWT.NONE);
        GridData gd_composite_1 = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd_composite_1.heightHint = 190;
        composite_1.setLayoutData(gd_composite_1);
        composite_1.setBackgroundMode(SWT.INHERIT_FORCE);
        FillLayout fillLayout = new FillLayout(SWT.HORIZONTAL);
        fillLayout.spacing = GUIParam.MAJOR_SPACING;
        fillLayout.marginWidth = GUIParam.MARGIN;
        fillLayout.marginHeight = GUIParam.MARGIN;
        composite_1.setLayout(fillLayout);

        _view = new CompTransfersTable(composite_1, SWT.NONE);

        Composite composite = new Composite(this, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        GridLayout gl_composite = new GridLayout(1, false);
        gl_composite.marginBottom = GUIParam.MARGIN;
        gl_composite.marginWidth = GUIParam.MARGIN;
        gl_composite.marginHeight = 0;
        composite.setLayout(gl_composite);

        _compStat = new CompTransferStat(composite, SWT.NONE);
        _compStat.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        createLabel(_compStat, SWT.NONE);
        createLabel(_compStat, SWT.NONE);
    }

    @Override
    public void dispose()
    {
        _view.dispose();

        super.dispose();
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }

    public void showSOCID(boolean enable)
    {
        _view.showSOCID(enable);
    }

    public void showDID(boolean enable)
    {
        _view.showDID(enable);
    }

    public void setTransferState(TransferState ts)
    {
        _view.setTransferState(ts);
    }
}
