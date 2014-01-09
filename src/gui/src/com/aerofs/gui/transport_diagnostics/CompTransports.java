/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.gui.Images;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;

import java.util.Map;

public class CompTransports extends Composite
{
    protected TableViewer _tableViewer;

    private TransportInfoProvider _provider;

    public CompTransports(Composite parent)
    {
        super(parent, SWT.NONE);

        _provider = new TransportInfoProvider();

        _tableViewer = new TableViewer(this, SWT.BORDER);
        _tableViewer.setContentProvider(new ArrayContentProvider());

        Table table = _tableViewer.getTable();
        table.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        table.setLinesVisible(false);

        TableViewerColumn colInfo = new TableViewerColumn(_tableViewer, SWT.NONE);
        colInfo.setLabelProvider(_provider);

        _tableViewer.setInput(Transport.values());
        _tableViewer.setSelection(new StructuredSelection(Transport.TCP));

        TableColumnLayout layout = new TableColumnLayout();
        layout.setColumnData(colInfo.getColumn(), new ColumnWeightData(1, 50, false));
        setLayout(layout);
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        _tableViewer.addSelectionChangedListener(listener);
    }

    @Override
    public void setData(Object data)
    {
        Preconditions.checkArgument(data == null || data instanceof TransportDiagnostics);

        super.setData(data);

        _provider.setData((TransportDiagnostics) data);
        _tableViewer.refresh();
    }

    public enum Transport
    {
        TCP("LAN", Images.ICON_SIGNAL3),
        JINGLE("WAN", Images.ICON_SIGNAL2),
        ZEPHYR("Relay", Images.ICON_SIGNAL1);

        protected String _label;
        protected Image _icon;

        private Transport(String label, String imageKey)
        {
            _label = label;
            _icon = Images.get(imageKey);
        }
    }

    protected static class TransportInfoProvider extends ColumnLabelProvider
    {
        /**
         * This map is used for _three_ pieces of information:
         * 1. If the map is null, then it means that we have not received any transport
         *   diagnostics yet.
         * 2. For each transport, if the entry corresponding to the transport is present, then the
         *   value is the number of reachable devices on that particular transport.
         * 3. For each transport, if the entry corresponding to the transport is absent, then it
         *   means that the transport is disabled.
         */
        protected Map<Transport, Integer> _deviceCounts;

        public TransportInfoProvider()
        {

        }

        public void setData(TransportDiagnostics reply)
        {
            if (_deviceCounts == null) {
                _deviceCounts = Maps.newEnumMap(Transport.class);
            }

            if (reply == null) {
                _deviceCounts.clear();
            } else {
                if (reply.hasTcpDiagnostics()) {
                    _deviceCounts.put(Transport.TCP,
                            reply.getTcpDiagnostics().getReachableDevicesCount());
                } else {
                    _deviceCounts.remove(Transport.TCP);
                }

                if (reply.hasJingleDiagnostics()) {
                    _deviceCounts.put(Transport.JINGLE,
                            reply.getJingleDiagnostics().getReachableDevicesCount());
                } else {
                    _deviceCounts.remove(Transport.JINGLE);
                }

                if (reply.hasZephyrDiagnostics()) {
                    _deviceCounts.put(Transport.ZEPHYR,
                            reply.getZephyrDiagnostics().getReachableDevicesCount());
                } else {
                    _deviceCounts.remove(Transport.ZEPHYR);
                }
            }
        }

        @Override
        public Image getImage(Object element)
        {
            Preconditions.checkArgument(element instanceof Transport);

            return ((Transport) element)._icon;
        }

        @Override
        public String getText(Object element)
        {
            Preconditions.checkArgument(element instanceof Transport);

            Transport transport = (Transport) element;

            StringBuilder builder = new StringBuilder()
                    .append(transport._label).append(" (");

            if (_deviceCounts == null) {
                builder.append("collecting information...");
            } else if (_deviceCounts.containsKey(transport)) {
                int count = _deviceCounts.get(transport);
                builder.append(count).append(" other computer");
                if (count != 1) builder.append('s');
            } else {
                builder.append("disabled");
            }

            builder.append(')');

            return builder.toString();
        }
    }
}
