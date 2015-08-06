/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.google.common.base.Preconditions;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Table;

import static com.aerofs.gui.GUIUtil.createLabel;

public class CompTCP extends AbstractCompTransport
{
    protected Label         _lblStatus;
    protected TblDevices    _tblDevices;

    public CompTCP(Composite parent)
    {
        super(parent);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Composite compStatus = createStatusComposite(content);
        Composite compDevices = createDevicesComposite(content);

        GridLayout layout = new GridLayout();
        layout.verticalSpacing = GUIParam.MAJOR_SPACING;
        content.setLayout(layout);

        compStatus.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        compDevices.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        return content;
    }

    protected Composite createStatusComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        _lblStatus = createLabel(composite, SWT.WRAP);
        _lblStatus.setFont(GUIUtil.makeBold(_lblStatus.getFont()));
        _lblStatus.setText(S.TXT_COLLECTING_NETWORK_INFO);

        Link lnkDesc = new Link(composite, SWT.NONE);
        lnkDesc.setText(S.LNK_TCP_DESC);
        lnkDesc.setFont(GUIUtil.makeSubtitle(lnkDesc.getFont()));
        lnkDesc.addSelectionListener(GUIUtil.createUrlLaunchListener(S.URL_TRANSPORTS_INFO));

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        composite.setLayout(layout);

        _lblStatus.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        lnkDesc.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        return composite;
    }

    protected Composite createDevicesComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblDevices = createLabel(composite, SWT.NONE);
        lblDevices.setText(S.LBL_REACHABLE_DEVICES);
        lblDevices.setFont(GUIUtil.makeBold(lblDevices.getFont()));

        _tblDevices = new TblDevices(composite);

        Link lnkDID = new Link(composite, SWT.NONE);
        lnkDID.setText(S.LNK_FIND_DEVICE_ID);
        lnkDID.addSelectionListener(GUIUtil.createUrlLaunchListener(S.URL_DEVICE_ID_INFO));

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        composite.setLayout(layout);

        lblDevices.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
        _tblDevices.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        lnkDID.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));

        return composite;
    }

    @Override
    public void setData(Object data)
    {
        Preconditions.checkArgument(data == null || data instanceof TransportDiagnosticsWithDeviceName);

        super.setData(data);

        if (data != null) {
            TransportDiagnosticsWithDeviceName d = (TransportDiagnosticsWithDeviceName) data;
            _lblStatus.setText(L.product() + " is listening on IP Address "
                    + formatAddress(d.getListeningAddress()));
            _tblDevices.setData(d.getTcpDevicesWithName());
        }
    }

    protected static class TblDevices extends Composite
    {
        protected TableViewer _tableViewer;

        public TblDevices(Composite parent)
        {
            super(parent, SWT.NONE);

            _tableViewer = new TableViewer(this, SWT.BORDER);
            _tableViewer.setContentProvider(new ArrayContentProvider());

            Table table = _tableViewer.getTable();
            table.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
            table.setLinesVisible(false);
            table.setHeaderVisible(true);

            TableViewerColumn colAddress = new TableViewerColumn(_tableViewer, SWT.LEFT);
            colAddress.getColumn().setText(S.LBL_COL_ADDRESS);
            colAddress.setLabelProvider(new TCPDeviceAddressProvider());

            TableViewerColumn colUserID = new TableViewerColumn(_tableViewer, SWT.LEFT);
            colUserID.getColumn().setText(S.LBL_COL_USER_ID);
            colUserID.setLabelProvider(new TCPDeviceUserIDProvider());

            TableViewerColumn colDeviceName = new TableViewerColumn(_tableViewer, SWT.LEFT);
            colDeviceName.getColumn().setText(S.LBL_COL_DEVICE_NAME);
            colDeviceName.setLabelProvider(new TCPDeviceNameProvider());

            TableColumnLayout layout = new TableColumnLayout();
            layout.setColumnData(colAddress.getColumn(), new ColumnWeightData(1, true));
            layout.setColumnData(colUserID.getColumn(), new ColumnWeightData(2, true));
            layout.setColumnData(colDeviceName.getColumn(), new ColumnWeightData(3, true));
            setLayout(layout);
        }

        @Override
        public void setData(Object data)
        {
            super.setData(data);

            _tableViewer.setInput(data);
        }
    }

    protected static class TCPDeviceUserIDProvider extends ColumnLabelProvider
    {
        @Override
        public String getText(Object element)
        {
            Preconditions.checkArgument(element instanceof TCPDeviceWithName);

            TCPDeviceWithName device = (TCPDeviceWithName) element;
            return device.getEmail();
        }
    }

    protected static class TCPDeviceAddressProvider extends ColumnLabelProvider
    {
        @Override
        public String getText(Object element)
        {
            Preconditions.checkArgument(element instanceof TCPDeviceWithName);
            TCPDeviceWithName device = (TCPDeviceWithName) element;

            return device.getIpAddress();
        }
    }

    protected static class TCPDeviceNameProvider extends ColumnLabelProvider
    {
        @Override
        public String getText(Object element)
        {
            Preconditions.checkArgument(element instanceof TCPDeviceWithName);

            TCPDeviceWithName device = (TCPDeviceWithName) element;
            return device.getDeviceName();
        }
    }
}
