/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.gui.transport_diagnostics.CompTransports.Transport;
import com.google.common.base.Preconditions;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

public class CompDetail extends Group implements ISelectionChangedListener
{
    protected final CompTCP     _compTCP;
    protected final CompZephyr  _compZephyr;
    protected final StackLayout _layout;

    public CompDetail(Composite parent)
    {
        super(parent, SWT.NONE);

        _compTCP = new CompTCP(this);
        _compZephyr = new CompZephyr(this);

        _layout = new StackLayout();
        _layout.topControl = _compTCP;
        setLayout(_layout);
    }

    // N.B. override to not throw exceptions
    @Override protected void checkSubclass() { }

    @Override
    public void setData(Object data)
    {
        Preconditions.checkArgument(data == null || data instanceof TransportDiagnosticsWithDeviceName);

        super.setData(data);

        if (data == null) {
            _compTCP.setData(null);
            _compZephyr.setData(null);
        } else {
            TransportDiagnosticsWithDeviceName td = (TransportDiagnosticsWithDeviceName) data;

            _compTCP.setData(td.hasTcpDiagnostics() ? td : null);
            _compZephyr.setData(td.hasZephyrDiagnostics() ? td : null);
        }
    }

    @Override
    public void selectionChanged(SelectionChangedEvent selectionChangedEvent)
    {
        IStructuredSelection selection =
                (IStructuredSelection) selectionChangedEvent.getSelection();
        Transport transport = (Transport) selection.getFirstElement();

        // N.B. transport can be null when the user is spamming clicks
        if (transport == null) return;

        switch (transport) {
        case TCP:
            _layout.topControl = _compTCP;
            break;
        case ZEPHYR:
            _layout.topControl = _compZephyr;
            break;
        default:
            return;
        }

        layout();
    }
}
