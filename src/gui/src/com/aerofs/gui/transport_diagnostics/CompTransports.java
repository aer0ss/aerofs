/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.gui.Images;
import com.aerofs.proto.Ritual.GetTransportDiagnosticsReply;
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

    /**
     * N.B. this map maintains _two_ pieces of information: whether a particular transport is
     *   enabled _and_ how many reachable devices there are on a particular transport.
     *
     * A transport is enabled _iff_ the map contains that transport as a key, and if a transport
     *   is disabled, the map _must not_ contain the corresponding key.
     */
    protected Map<Transport, Integer> _deviceCount;

    public CompTransports(Composite parent)
    {
        super(parent, SWT.NONE);

        _deviceCount = Maps.newEnumMap(Transport.class);

        _tableViewer = new TableViewer(this, SWT.BORDER);
        _tableViewer.setContentProvider(new ArrayContentProvider());

        Table table = _tableViewer.getTable();
        table.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        table.setLinesVisible(false);

        TableViewerColumn colInfo = new TableViewerColumn(_tableViewer, SWT.NONE);
        colInfo.setLabelProvider(new TransportInfoProvider(_deviceCount));

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
        Preconditions.checkArgument(data == null || data instanceof GetTransportDiagnosticsReply);

        super.setData(data);

        if (data == null) {
            _deviceCount.clear();
        } else {
            GetTransportDiagnosticsReply reply = (GetTransportDiagnosticsReply) data;

            if (reply.hasTcpDiagnostics()) {
                _deviceCount.put(Transport.TCP,
                        reply.getTcpDiagnostics().getReachableDevicesCount());
            } else {
                _deviceCount.remove(Transport.TCP);
            }

            if (reply.hasJingleDiagnostics()) {
                _deviceCount.put(Transport.JINGLE,
                        reply.getJingleDiagnostics().getReachableDevicesCount());
            } else {
                _deviceCount.remove(Transport.JINGLE);
            }

            if (reply.hasZephyrDiagnostics()) {
                _deviceCount.put(Transport.ZEPHYR,
                        reply.getZephyrDiagnostics().getReachableDevicesCount());
            } else {
                _deviceCount.remove(Transport.ZEPHYR);
            }
        }

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
        protected Map<Transport, Integer> _deviceCounts;

        public TransportInfoProvider(Map<Transport, Integer> deviceCounts) {
            _deviceCounts = deviceCounts;
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

            if (_deviceCounts.containsKey(transport)) {
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
