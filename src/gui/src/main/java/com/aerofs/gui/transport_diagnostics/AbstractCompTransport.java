/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.S;
import com.aerofs.proto.Diagnostics.PBInetSocketAddress;
import com.aerofs.proto.Diagnostics.ServerStatus;
import com.google.common.base.Preconditions;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import javax.annotation.Nonnull;

import static com.aerofs.gui.GUIUtil.createLabel;

public abstract class AbstractCompTransport extends Composite
{
    protected StackLayout _layout;

    protected Composite _compMessage;
    protected CLabel    _lblMessage;
    protected Composite _compContent;

    protected AbstractCompTransport(Composite parent)
    {
        super(parent, SWT.NONE);

        _compContent = createContent(this);
        _compMessage = createMessageComposite(this);
        _lblMessage.setText(S.ERR_TRANSPORT_DISABLED);

        _layout = new StackLayout();
        _layout.topControl = _compContent;
        setLayout(_layout);
    }

    protected abstract @Nonnull Composite createContent(Composite parent);

    protected Composite createMessageComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        _lblMessage = new CLabel(composite, SWT.NONE);
        _lblMessage.setImage(Images.get(Images.ICON_ERROR));

        composite.setLayout(new GridLayout());
        _lblMessage.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

        return composite;
    }

    @Override
    public void setData(Object data)
    {
        super.setData(data);

        _layout.topControl = data == null ? _compMessage : _compContent;

        layout();
    }

    protected static String formatAddress(PBInetSocketAddress address)
    {
        Preconditions.checkArgument(address.hasHost() && address.hasPort());

        StringBuilder output = new StringBuilder()
                .append(address.getHost());

        output.append(':').append(address.getPort());

        return output.toString();
    }

    protected static String formatServerStatus(ServerStatus status)
    {
        Preconditions.checkArgument(status.hasReachable());

        // only show server status details in private deployment
        return formatAddress(status.getServerAddress())
                + " (" + (status.getReachable() ? "connected" : "disconnected") + ")";
    }

    /**
     * _Hack Alert_(AT): the status decorators are intended to be used like composites, but it isn't
     *   a composite. The main problem is that composites _must_ have their own layout for their
     *   children. Whereas for status decorators, we want to use the parent's layout so the
     *   children from different status decorators will be horizontally aligned.
     *
     * This class is used to add & setup children for a composite. The composite must
     *   be using GridLayout with 2 columns, and the children will take up a 2x2 block of
     *   grid cells.
     */
    protected static class StatusDecorator
    {
        protected Label _lblText;
        protected Label _lblStatus;
        protected Link  _lnkDesc;

        public StatusDecorator(Composite parent)
        {
            _lblText = createLabel(parent, SWT.NONE);
            _lblStatus = createLabel(parent, SWT.WRAP);
            _lblStatus.setFont(GUIUtil.makeBold(_lblStatus.getFont()));
            _lnkDesc = new Link(parent, SWT.WRAP);
            _lnkDesc.setFont(GUIUtil.makeSubtitle(_lnkDesc.getFont()));

            _lblText.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 2));
            _lblStatus.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            _lnkDesc.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        }

        public void setText(String text)
        {
            _lblText.setText(text);
        }

        public void setStatus(String status)
        {
            _lblStatus.setText(status);
            _lblStatus.getParent().layout();
        }

        public void setDescription(String desc)
        {
            _lnkDesc.setText(desc);
        }

        public void addSelectionListener(SelectionListener listener)
        {
            _lnkDesc.addSelectionListener(listener);
        }
    }

    /**
     * see StatusDecorator, also takes up a 2x2 block of grid cells.
     */
    protected static class DevicesDecorator
    {
        protected Label _lblDevices;
        protected TblDevices _tblDevices;
        protected Link  _lnkDevices;

        protected DevicesDecorator(Composite parent)
        {
            _lblDevices = createLabel(parent, SWT.NONE);
            _lblDevices.setText(S.LBL_REACHABLE_DEVICES);

            _tblDevices = new TblDevices(parent);

            _lnkDevices = new Link(parent, SWT.NONE);
            _lnkDevices.setText(S.LNK_FIND_DEVICE_ID);
            _lnkDevices.addSelectionListener(GUIUtil.createUrlLaunchListener(S.URL_DEVICE_ID_INFO));

            _lblDevices.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 2));
            _tblDevices.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            _lnkDevices.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));
        }

        public void setData(Object data)
        {
            _tblDevices.setData(data);
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

                TableViewerColumn colUser = new TableViewerColumn(_tableViewer, SWT.LEFT);
                colUser.getColumn().setText(S.LBL_COL_USER_ID);
                colUser.setLabelProvider(new ZephyrUserProvider());

                TableViewerColumn colDeviceName = new TableViewerColumn(_tableViewer, SWT.LEFT);
                colDeviceName.getColumn().setText(S.LBL_COL_DEVICE_NAME);
                colDeviceName.setLabelProvider(new ZephyrDeviceProvider());

                TableColumnLayout layout = new TableColumnLayout();
                layout.setColumnData(colUser.getColumn(), new ColumnWeightData(1, true));
                layout.setColumnData(colDeviceName.getColumn(), new ColumnWeightData(2, true));
                setLayout(layout);
            }

            @Override
            public void setData(Object data)
            {
                super.setData(data);

                _tableViewer.setInput(data);
            }

        }

        protected static class ZephyrUserProvider extends ColumnLabelProvider
        {
            @Override
            public String getText(Object element)
            {
                Preconditions.checkArgument(element instanceof ZephyrDeviceWithName);
                ZephyrDeviceWithName device = (ZephyrDeviceWithName) element;

                return device.getEmail();
            }
        }

        protected static class ZephyrDeviceProvider extends ColumnLabelProvider
        {
            @Override
            public String getText(Object element)
            {
                Preconditions.checkArgument(element instanceof ZephyrDeviceWithName);
                ZephyrDeviceWithName device = (ZephyrDeviceWithName) element;

                return device.getDeviceName();
            }
        }
    }
}
