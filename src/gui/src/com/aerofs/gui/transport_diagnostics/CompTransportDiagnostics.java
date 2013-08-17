/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.gui.GUIUtil;
import com.aerofs.proto.Ritual.GetTransportDiagnosticsReply;
import com.google.common.base.Preconditions;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;

public class CompTransportDiagnostics extends SashForm
{
    protected CompTransports    _compTransports;
    protected CompDetail        _compDetail;

    public CompTransportDiagnostics(Composite parent)
    {
        super(parent, SWT.HORIZONTAL);

        _compTransports = createCompTransports(this);
        _compDetail = new CompDetail(this);

        setWeights(new int[]{1, 3});
        setSashWidth(8);

        _compTransports.addSelectionChangedListener(_compDetail);
    }

    // overriden to propagate data to its children
    @Override
    public void setData(Object data)
    {
        Preconditions.checkArgument(data == null || data instanceof GetTransportDiagnosticsReply);

        super.setData(data);

        _compTransports.setData(data);
        _compDetail.setData(data);
    }

    /**
     * N.B. this is a workaround for platform-specific group margin non-sense

     * unfortunately, the best we can do is to add margins to compTransports in an attempt to line
     * it up with a group.
     */
    protected CompTransports createCompTransports(Composite parent)
    {
        Composite container = GUIUtil.createGroupAligningContainer(parent);
        CompTransports comp = new CompTransports(container);
        comp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        return comp;
    }
}
